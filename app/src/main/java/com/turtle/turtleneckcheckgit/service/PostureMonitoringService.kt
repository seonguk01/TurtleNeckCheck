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
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.turtle.turtleneckcheckgit.R
import com.turtle.turtleneckcheckgit.WarningActivity
import com.turtle.turtleneckcheckgit.common.ActionIntent
import com.turtle.turtleneckcheckgit.receiver.WidgetReceiver
import handasoft.mobile.divination.module.pref.SharedPreference.putSharedPreference
import java.lang.Math.toDegrees
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt
import android.content.res.Configuration

class PostureMonitoringService : Service(), SensorEventListener, LifecycleOwner {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handler: Handler
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private var CHANNEL_ID = "turtle_neck"
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private var phoneTiltXAngle = 0f // 스마트폰의 기울기 값을 저장
    private var phoneTiltYAngle = 0f // 스마트폰의 기울기 값을 저장
    private var monitoringTime: Long = 10 * 1000 // 10분
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
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)!! //중력 가속도만 체크

        // 자이로 센서 등록
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
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

    private fun isLandscapeMode(): Boolean {
        val orientation = resources.configuration.orientation
        return orientation == Configuration.ORIENTATION_LANDSCAPE
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


                        /**
                         *  24.9.10 facePosition: facePosition은 사용자의 얼굴에서 **코 끝 (Nose Base)**의 위치를 나타냅니다.
                         * 이 위치는 FaceLandmark.NOSE_BASE를 사용하여 얻습니다. position 속성은 X, Y 좌표로 나타나며, X는 화면 좌우(수평), Y는 화면 위아래(수직)를 나타냅니다.

                         * shoulderPosition: 이 위치는 어깨의 대략적인 위치를 추정하기 위해 사용합니다.
                         *  facePosition의 Y 좌표에서 150픽셀 정도 아래로 가정하여 어깨가 있다고 간주합니다.

                         * 실제 어깨를 감지하는 것이 아니라, 단순히 목이 얼마나 앞으로 빠졌는지를 계산하기 위해 어깨 위치를 임의로 설정하는 것입니다.
                         */
                        //facePosition은 사용자의 얼굴에서 특정 지점(예: 코 끝)의 좌표입니다. 이 좌표는 얼굴의 기울기뿐만 아니라 얼굴 위치를 기반으로 추가적인 계산을 위해 사용됩니다.
                        //FaceLandmark.NOSE_BASE는 코의 기초 부분(코 밑 부분)의 위치를 의미합니다.
                        val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)
                        val rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR)
                        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

                        if (leftEar != null && rightEar != null && noseBase != null && leftEye != null && rightEye != null) {
                            // 얼굴의 넓이를 계산 (양쪽 귀 사이 거리)
                            val faceWidth = rightEar.position.x - leftEar.position.x

                            // 눈 중심 계산
                            val eyeCenterY = (leftEye.position.y + rightEye.position.y) / 2

                            // deltaY를 얼굴 크기로 정규화하여 카메라 거리 영향 최소화
                            val deltaY = (noseBase.position.y - eyeCenterY) / faceWidth

                            // 목이 앞으로 쏠린 각도 계산
                            val angle = toDegrees(atan2(deltaY.toDouble(), 1.0)) // deltaX는 1.0으로 고정하여 비율만 사용

                            val neckTiltAngle = 90 - (phoneTiltYAngle + headTiltX)  // 폰 기울기 (Y축) + 얼굴 기울기


                            Log.e("PostureMonitoringService", "headTiltX: $headTiltX")
                            Log.e("PostureMonitoringService", "neckTiltAngle: $neckTiltAngle")
                            Log.e("PostureMonitoringService", "phoneTiltYAngle: $phoneTiltYAngle")
                            Log.e("PostureMonitoringService", "phoneTiltXAngle: $phoneTiltXAngle")
                            when {
                                phoneTiltYAngle in 70f..90f && (neckTiltAngle > 20 || angle > 20) -> {
                                    conditionMet = true
                                }
                                phoneTiltYAngle in 60f..70f && (neckTiltAngle > 28 || angle > 20) -> {
                                    conditionMet = true
                                }
                                phoneTiltYAngle in 45f..60f && (neckTiltAngle > 35 || angle > 20) -> {
                                    conditionMet = true
                                }
                                phoneTiltYAngle in 35f..45f && (neckTiltAngle > 42 || angle > 20) -> {
                                    conditionMet = true
                                }
                                phoneTiltYAngle in 30f..35f && (neckTiltAngle > 47 || angle > 20) -> {
                                    conditionMet = true
                                }
                                phoneTiltYAngle <= 30f && (neckTiltAngle > 50 || angle > 20) -> {
                                    conditionMet = true
                                }
                            }

                            if (conditionMet) {
                                showWarningPopup()
                                break  // Exit the loop as soon as one condition is met
                            }
                        }
                    }
                }else{
                    //얼굴 인식 실패
                    Log.e("PostureMonitoringService", "checkFaceTiltAndPosition: '얼굴 인식이 실패 했습니다.")
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

            // 현재 화면 방향에 따라 각도 계산 방식 조정
            if (isLandscapeMode()) {
                // 가로 모드일 때, X와 Y 축을 바꿔서 계산
                phoneTiltXAngle = (90 - acos(y/r) * 180 / PI).toFloat() // 가로모드에서 Y축을 X축으로 취급
                phoneTiltYAngle = (90 - acos(x/r) * 180 / PI).toFloat() // 가로모드에서 X축을 Y축으로 취급
            } else {
                // 세로 모드일 때, 기존대로 처리
                phoneTiltXAngle = (90 - acos(x/r) * 180 / PI).toFloat()
                phoneTiltYAngle = (90 - acos(y/r) * 180 / PI).toFloat()
            }

            Log.e(
                "TAG",
                "onSensorChanged: $x  , y: $y, z: $z, r : $r [xAngle : $phoneTiltXAngle, yAngle $phoneTiltYAngle]"
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}