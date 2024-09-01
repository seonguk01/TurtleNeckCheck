package com.turtle.turtleneckcheckgit.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.turtle.turtleneckcheckgit.R
import com.turtle.turtleneckcheckgit.WarningActivity
import com.turtle.turtleneckcheckgit.common.ActionIntent
import com.turtle.turtleneckcheckgit.util.Util.isAppInForeground
import handasoft.mobile.divination.module.pref.SharedPreference
import handasoft.mobile.divination.module.pref.SharedPreference.putSharedPreference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

class PostureMonitoringService : Service(), SensorEventListener, LifecycleOwner {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handler: Handler
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor

    // LifecycleRegistry를 초기화
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    private var phoneTilt = 0f // 스마트폰의 기울기 값을 저장
    private var monitoringTime: Long =   10 * 60* 1000 // 10분
    private val binder = LocalBinder()
    private var lastPopupTime: Long = 0


    inner class LocalBinder : Binder() {
        fun getService(): PostureMonitoringService = this@PostureMonitoringService
    }
    private val _sensorValue = MutableLiveData<Float>()
    val sensorValue: LiveData<Float> = _sensorValue
    private val _sensorFinalValue = MutableLiveData<String>()
    val sensorFinalValue: LiveData<String> = _sensorFinalValue

    private val _faceAngleX = MutableLiveData<Float>()
    val faceAngleX: LiveData<Float> = _faceAngleX

    private val _faceAngleZ = MutableLiveData<Float>()
    val faceAngleZ: LiveData<Float> = _faceAngleZ

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        cameraExecutor = Executors.newSingleThreadExecutor()
        handler = Handler(mainLooper)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        // 자이로 센서 등록
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        val intentAction = intent?.action
        Log.e("handa_log", "onStartCommand: $intentAction")
        when (intentAction) {
            ActionIntent.STOPFOREGROUND_ACTION -> {
                stopService()
                putSharedPreference(applicationContext,"service_enable",false)

            }
            ActionIntent.STARTFOREGROUND_ACTION ->{
                putSharedPreference(applicationContext,"service_enable",true)

                Log.e("handa_log", "onStartCommand 1: $intentAction")

                startForegroundService()
                startMonitoring()
            }
            else -> {
            }
        }
        return START_REDELIVER_INTENT
    }
    private fun stopService() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
        // 자이로센서 해제
        sensorManager.unregisterListener(this)
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                "TURTLE_NECK",
                "거북목 테스트",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Monitoring your posture to prevent neck strain."
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)

            val notification = NotificationCompat.Builder(this, "TURTLE_NECK")
                .setContentTitle("Posture Monitoring Active")
                .setContentText("Monitoring your posture in the background.")
                .setSmallIcon(R.drawable.ic_warning)
                .build()

            startForeground(1, notification)
        }
    }

    private fun startMonitoring() {
        // 10분 후 기울기 체크
        handler.postDelayed({
            startCamera()
        }, monitoringTime)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)

            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("PostureMonitoringService", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        // ML Kit을 사용해 얼굴 인식 및 기울기 계산
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            checkFaceTilt(image)
        }
        imageProxy.close()
    }

    private fun checkFaceTilt(image: InputImage) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()

        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                var conditionMet = false

                Log.d("FaceDetection", "${phoneTilt}PostureMonitoringService: faces :$faces")
                if (faces.isNotEmpty()) {
                    // 얼굴이 인식된 경우
                    for (face in faces) {
                        // 얼굴 기울기 및 추가 정보 처리
                        val headTiltX = face.headEulerAngleX
                        val headTiltZ = face.headEulerAngleZ
                        Log.d("FaceDetection", "${phoneTilt}PostureMonitoringService: headTiltX :$headTiltX")
                        _faceAngleX.postValue(headTiltX)
                        _faceAngleZ.postValue(headTiltZ)
                        // 얼굴이 일정 각도 이상 기울어진 경우 처리
                        //headTiltX 이 -값이 될수록 얼굴이 아래로 기울인 상태
                        //PhoneTilt값이 양수이면 화면이 위로 기울여진 상태
                        if (phoneTilt >= 4 && headTiltX<10) {
//                            Toast.makeText(this, "거북목 조심하세요", Toast.LENGTH_LONG).show()
                            showWarningPopup()
                            conditionMet = true
                        }
                    }
                } else {
                    // 얼굴이 인식되지 않은 경우
                    Log.d("FaceDetection", " PostureMonitoringService : 얼굴이 인식되지 않았습니다.")
                }
                Log.d("FaceDetection", " PostureMonitoringService : $conditionMet.")

                if(!conditionMet){
                    startMonitoring()
                }else{
                    stopCamera()
                    stopSensorMonitoring()
                    startMonitoring()

                }

            }
            .addOnFailureListener {
                // 오류 처리
              /*  e->
                Log.e("FaceDetection", " PostureMonitoringService : $e.")*/
                startMonitoring()
            }
    }
    //카메라 사용 최적화
    //얼굴을 한번이라도 인식을 성공하면 카메라 기능을 끈다.
    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()  // 카메라 사용 중지
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopSensorMonitoring() {
       /* lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)*/
        // 자이로센서 해제
        sensorManager.unregisterListener(this)// 센서 리스너 해제
    }
    private fun showWarningPopup() {
        val currentTIme = System.currentTimeMillis()
        Log.d("FaceDetection", " showWarningPopup : ${currentTIme - lastPopupTime >= monitoringTime}.")

        if(currentTIme - lastPopupTime >= monitoringTime){
            lastPopupTime = currentTIme //팝업이 뜬후 마지막 시간을 저장한다

//            if(!isAppInForeground(this)){
            Intent(this, WarningActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP )

            }.also {
                startActivity(it)
            }
//            }


        }


    }


    // 자이로센서 데이터 수신
    override fun onSensorChanged(event: SensorEvent?) {

        event?.let {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val r = sqrt(x.pow(2) + y.pow(2) + z.pow(2))

            Log.d("MainActivity", "onSensorChanged: x: $x, y: $y, z: $z, R: $r")
            val xrAngle = (90 - acos(x / r) * 180 / PI).toFloat()
            val yrAngle = (90 - acos(y / r) * 180 / PI).toFloat()
            phoneTilt = abs(event.values[2])
            _sensorValue.postValue(abs(y))
            _sensorFinalValue.postValue("$xrAngle,$yrAngle")
//            binding.textview.text = String.format(
//                "x-rotation: %.1f\u00B0 \n y-rotation: %.1f\u00B0", xrAngle, yrAngle)
        }

        /*   event?.let {
               // X축과 Z축의 기울기를 통해 스마트폰이 얼마나 기울어졌는지 계산
               phoneTilt = Math.abs(event.values[1]) // Y축 (Pitch)
           }*/
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
