package com.turtle.turtleneckcheckgit.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.turtle.turtleneckcheckgit.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PostureMonitoringService : Service(), SensorEventListener, LifecycleOwner {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var handler: Handler
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor

    // LifecycleRegistry를 초기화
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    private var phoneTilt = 0f // 스마트폰의 기울기 값을 저장
    private var monitoringTime: Long = 1 * 1000 // 10분
    private val binder = LocalBinder()

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
        startForegroundService()
        startMonitoring()
        return START_STICKY
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
                "POSTURE_MONITORING_CHANNEL",
                "Posture Monitoring",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Monitoring your posture to prevent neck strain."
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)

            val notification = NotificationCompat.Builder(this, "POSTURE_MONITORING_CHANNEL")
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

            imageAnalysis.setAnalyzer(cameraExecutor, {
                imageProxy ->
                processImage(imageProxy)

            })

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
                Log.d("FaceDetection", "${phoneTilt}PostureMonitoringService: faces :$faces")

                if (faces.isNotEmpty()) {
                    // 얼굴이 인식된 경우
                    for (face in faces) {
                        // 얼굴 기울기 및 추가 정보 처리
                        val headTiltX = face.headEulerAngleX
                        val headTiltZ = face.headEulerAngleZ
                        Log.d("FaceDetection", "${phoneTilt}PostureMonitoringService: headTiltX :$headTiltX")

                        // 얼굴이 일정 각도 이상 기울어진 경우 처리
                        if (phoneTilt <8.5) {
                            showWarningPopup()
                        }
                    }
                } else {
                    // 얼굴이 인식되지 않은 경우
                    Log.d("FaceDetection", " PostureMonitoringService : 얼굴이 인식되지 않았습니다.")
                }
            }
            .addOnFailureListener {
                // 오류 처리
            }
    }

    private fun showWarningPopup() {
        Toast.makeText(this, "거북목 조심하세요", Toast.LENGTH_LONG).show()
    }

    // 자이로센서 데이터 수신
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // X축과 Z축의 기울기를 통해 스마트폰이 얼마나 기울어졌는지 계산
            phoneTilt = Math.abs(event.values[1]) // Y축 (Pitch)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
