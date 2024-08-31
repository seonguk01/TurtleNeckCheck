package com.turtle.turtleneckcheckgit.service

import android.Manifest
import android.app.Notification
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
import com.turtle.turtleneckcheckgit.common.Constant
import com.turtle.turtleneckcheckgit.receiver.WidgetReceiver
import com.turtle.turtleneckcheckgit.type.ServiceType
import com.turtle.turtleneckcheckgit.util.Util.isAppInForeground
import handasoft.mobile.divination.module.pref.SharedPreference
import handasoft.mobile.divination.module.pref.SharedPreference.putSharedPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var CHANNEL_ID = "turtle_neck"
    // LifecycleRegistry를 초기화
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    private var phoneTilt = 0f // 스마트폰의 기울기 값을 저장
    private var monitoringTime: Long =   1 * 60* 1000 // 10분
    private val binder = LocalBinder()
    private var lastPopupTime: Long = 0


    inner class LocalBinder : Binder() {
        fun getService(): PostureMonitoringService = this@PostureMonitoringService
    }

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
        var isEnableService = SharedPreference.getBooleanSharedPreference(this@PostureMonitoringService,"service_enable")
        CoroutineScope(Dispatchers.Main).launch {
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


        }
        return START_REDELIVER_INTENT


        /*if(isEnableService){
            startForegroundService()
            startMonitoring()
        }else{
            stopService()
        }

        return START_STICKY*/
    }
    private fun stopService() {
        try {
            stopCamera()
            stopSensorMonitoring()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        stopService()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
        // 자이로센서 해제
        sensorManager.unregisterListener(this)
    }


    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "거북목"
            val descriptionText = ""
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notificationIntent = Intent(ActionIntent.ACTION_SERVICE_DEFAULT)
        notificationIntent.setClass(this@PostureMonitoringService, WidgetReceiver::class.java)
        val pi : PendingIntent =  PendingIntent.getBroadcast(this@PostureMonitoringService, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or 0)
        var builder = NotificationCompat.Builder(this, CHANNEL_ID)
        builder.setContentIntent(pi)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setContentTitle(getString(R.string.service_title))
            builder  .setTicker(getString(R.string.service_title))
            builder .setContentText(getString(R.string.service_content))
            builder .setSmallIcon(R.drawable.ic_warning)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.priority = NotificationManager.IMPORTANCE_MIN
            }else{
                builder.priority = Notification.PRIORITY_MIN
            }
            builder .setChannelId(CHANNEL_ID)
            builder.color = ContextCompat.getColor(this@PostureMonitoringService, R.color.black)
            builder .setAutoCancel(false)
        } else {
            builder.setContentTitle(getString(R.string.service_title))
            builder .setTicker(getString(R.string.service_title))
            builder  .setContentText(getString(R.string.service_content))
            builder  .setSmallIcon(R.drawable.ic_warning)
            builder  .setChannelId(CHANNEL_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.priority = NotificationManager.IMPORTANCE_MIN
            }else{
                builder.priority = Notification.PRIORITY_MIN
            }
            builder  .setAutoCancel(false)
        }
        builder.setNumber(0)
        val notification = builder.build()



        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                return
            }
            notify(1000, notification)
        }

// Notification ID cannot be 0.
        startForeground(1000, notification)
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
                        Log.d("FaceDetection", "${phoneTilt}PostureMonitoringService: headTiltX :$headTiltX ,$headTiltZ")
                        // 얼굴이 일정 각도 이상 기울어진 경우 처리
                        //headTiltX 이 -값이 될수록 얼굴이 아래로 기울인 상태
                        //PhoneTilt값이 양수이면 화면이 위로 기울여진 상태
                        if (phoneTilt >= 4 && headTiltX<10) {
                            Toast.makeText(this, "거북목 조심하세요", Toast.LENGTH_LONG).show()
                            showWarningPopup()
                            conditionMet = true
                        }
                    }
                } else {
                    // 얼굴이 인식되지 않은 경우
                    Log.d("FaceDetection", " PostureMonitoringService : 얼굴이 인식되지 않았습니다.")
                }
                Log.d("FaceDetection", " PostureMonitoringService : $conditionMet.")

                stopCamera() //카메라 인식 중지 후 다시 일정시간 지난후 다시 호출하기 위한 목적
                stopSensorMonitoring()  // 얼굴 인식을 완료하고 센서 모니터링 중지
                startMonitoring() //일정 시간이 지난후 다시 모니터링 시작
            }
            .addOnFailureListener {
                // 오류 처리
                stopCamera()
                stopSensorMonitoring()  // 얼굴 인식을 완료하고 센서 모니터링 중지
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
        sensorManager.unregisterListener(this, accelerometer)  // 센서 리스너 해제
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
            phoneTilt = abs(event.values[2])
        }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
