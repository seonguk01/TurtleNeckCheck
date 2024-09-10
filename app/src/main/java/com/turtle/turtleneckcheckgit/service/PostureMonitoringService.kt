package com.turtle.turtleneckcheckgit.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
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
import com.google.mlkit.vision.face.FaceLandmark
import com.turtle.turtleneckcheckgit.R
import com.turtle.turtleneckcheckgit.WarningActivity
import com.turtle.turtleneckcheckgit.common.ActionIntent
import com.turtle.turtleneckcheckgit.receiver.WidgetReceiver
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
    private var CHANNEL_ID = "turtle_neck"
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    private var phoneTilt = 0f // 스마트폰의 기울기 값을 저장
    private var monitoringTime: Long = 10 *60 * 1000 // 10분
    private val binder = LocalBinder()
    private var lastPopupTime: Long = 0
    val thresholdDistance = 150f // 목이 앞으로 빠진 정도를 수치화할 때 중요한 기준이 됩니다.
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
        val intentAction = intent?.action
        Log.e("handa_log", "onStartCommand: $intentAction")
        when (intentAction) {
            ActionIntent.STOPFOREGROUND_ACTION -> {
                stopService()
                putSharedPreference(applicationContext, "service_enable", false)
            }
            ActionIntent.STARTFOREGROUND_ACTION -> {
                putSharedPreference(applicationContext, "service_enable", true)

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
        sensorManager.unregisterListener(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "거북목"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notificationIntent = Intent(ActionIntent.ACTION_SERVICE_DEFAULT)
        notificationIntent.setClass(this@PostureMonitoringService, WidgetReceiver::class.java)
        val pi: PendingIntent = PendingIntent.getBroadcast(
            this@PostureMonitoringService, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or 0
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentIntent(pi)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_content))
            .setSmallIcon(R.drawable.ic_warning)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.color = ContextCompat.getColor(this@PostureMonitoringService, R.color.black)
        }

        val notification = builder.build()

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(1000, notification)
        }

        startForeground(1000, notification)
    }

    private fun startMonitoring() {
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
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            checkFaceTiltAndPosition(image)
        }
        imageProxy.close()
    }

    private fun checkFaceTiltAndPosition(image: InputImage) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)  // 더 정확한 모드
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)  // 모든 랜드마크를 감지
            .build()

        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                var conditionMet = false

                if (faces.isNotEmpty()) {
                    //얼굴 감지 하는 루프
                    for (face in faces) {
                        val headTiltX = face.headEulerAngleX  //앞뒤로 얼마나 기울어져 있는지 나타내는 값,X축을 기준으로 얼굴의 기울기를 나타내며, 값이 음수일수록 얼굴이 아래쪽으로 기울어진 상태입니다.
                        val headTiltZ = face.headEulerAngleZ //headTiltZ는 사용자의 얼굴이 좌우로 얼마나 기울어져 있는지를 나타내는 값입니다. 이 값은 Y축을 기준으로 얼굴의 좌우 기울기를 나타냅니다. 이 코드에서는 경고를 위한 조건에는 포함되지 않지만, 추가적인 목 자세 분석에 사용될 수 있습니다.

                        //facePosition은 사용자의 얼굴에서 특정 지점(예: 코 끝)의 좌표입니다. 이 좌표는 얼굴의 기울기뿐만 아니라 얼굴 위치를 기반으로 추가적인 계산을 위해 사용됩니다.
                        //FaceLandmark.NOSE_BASE는 코의 기초 부분(코 밑 부분)의 위치를 의미합니다.
                        val facePosition = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
                        // Assume we have a fixed body position for simplicity
                        //이 부분은 어깨의 위치를 추정하는 코드입니다.
                        //shoulderPosition은 코 끝의 좌표에서 수직으로 아래로 150픽셀 떨어진 위치에 어깨가 있다고 가정하여 계산됩니다.
                        //이는 실제 어깨 위치를 감지하는 것이 아니라 단순히 목의 앞으로 빠진 정도를 가늠하기 위해 대략적인 값을 사용하는 것입니다.
                        val shoulderPosition = facePosition?.let {
                            PointF(it.x, it.y + 150)
                        }

                        Log.e("PostureMonitoringService", "headTiltX: $headTiltX", )
                        Log.e("PostureMonitoringService", "phoneTilt: $phoneTilt", )

                        Log.e("PostureMonitoringService", "FaceDetection: $facePosition", )
                        Log.e("PostureMonitoringService", "FaceDetection_shoulderPosition: $shoulderPosition", )

                        if (facePosition != null && shoulderPosition != null) {
                            //이 부분은 얼굴의 특정 지점(코 끝)과 추정된 어깨 위치 사이의 거리를 계산합니다.
                            //피타고라스 정리를 사용하여 두 점 사이의 거리를 계산합니다.
                            //이 거리가 줄어들수록 사용자의 목이 더 앞으로 빠져 있다는 것을 의미합니다.
                            val distance = sqrt(
                                (facePosition.x - shoulderPosition.x).pow(2) +
                                        (facePosition.y - shoulderPosition.y).pow(2)
                            )
                            Log.e("PostureMonitoringService", "distance: $distance", )
                            Log.e("PostureMonitoringService", "thresholdDistance: $thresholdDistance", )

                            //phoneTilt >= 4: 휴대폰이 4도 이상 기울어져 있을 때를 의미합니다. 이것은 사용자가 화면을 내려다보고 있을 가능성이 높다는 것을 시사합니다.
                            //headTiltX < 10: 사용자의 얼굴이 10도 이상 앞으로 숙여졌을 때를 의미합니다. 값이 음수일수록 더 숙인 상태입니다.
                            //distance < thresholdDistance: 얼굴과 어깨 사이의 거리가 thresholdDistance보다 작을 때, 즉, 목이 앞으로 많이 빠진 상태를 의미합니다.
                            if (phoneTilt >= 4 && headTiltX < 10 ||
                                distance < thresholdDistance) {
                                showWarningPopup()
                                conditionMet = true
                            }
                        }
                    }
                }else{
                    //얼굴 인식 실패
                    Log.e("PostureMonitoringService", "checkFaceTiltAndPosition: '얼굴 인식이 실패 했습니다.", )
                }

                if (!conditionMet) {
                    startMonitoring()
                } else {
                    stopCamera()
                    stopSensorMonitoring()
                    startMonitoring()
                }
            }
            .addOnFailureListener {
                startMonitoring()
            }
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
    }

    private fun showWarningPopup() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastPopupTime >= monitoringTime) {
            lastPopupTime = currentTime

            Intent(this, WarningActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }.also {
                startActivity(it)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val r = sqrt(x.pow(2) + y.pow(2) + z.pow(2))

            phoneTilt = abs(event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
