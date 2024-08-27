package com.turtle.turtleneckcheckgit

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.turtle.turtleneckcheckgit.databinding.ActivityMainBinding
import com.turtle.turtleneckcheckgit.module.PermissionCheckerManager
import com.turtle.turtleneckcheckgit.service.PostureMonitoringService
import handasoft.mobile.divination.module.pref.SharedPreference

class MainActivity : AppCompatActivity(), PermissionCheckerManager.PermissionListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionHelper: PermissionCheckerManager
    var  isService : Boolean= false;

    private var postureMonitoringService : PostureMonitoringService ?= null
    private var  isBound = false

    private  val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PostureMonitoringService.LocalBinder
            postureMonitoringService = binder.getService()
            isBound = true

            postureMonitoringService?.sensorValue?.observe(this@MainActivity, Observer {
                value -> binding.tvSensorValue.text = "$value"
            })
            postureMonitoringService?.sensorFinalValue?.observe(this@MainActivity, Observer {
                    value -> binding.tvSensorConvertValue.text = "$value"
            })
            postureMonitoringService?.faceAngleX?.observe(this@MainActivity, Observer {
                    value -> binding.tvFaceAngleValue.text = "$value"
            })
            postureMonitoringService?.faceAngleZ?.observe(this@MainActivity, Observer {
                    value -> binding.tvFaceAngleYValue.text = "$value"
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.FOREGROUND_SERVICE_CAMERA


        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        binding.tvSensorValue.text =""
        binding.tvFaceAngleValue.text =""
        permissionHelper = PermissionCheckerManager(this, requiredPermissions.toTypedArray(), 100)
        permissionHelper.requestPermissionsIfNecessary()
        val serviceIntent = Intent(this, PostureMonitoringService::class.java)

        binding.btnServiceStart.setOnClickListener {
            if(!isService){
                SharedPreference.putSharedPreference(this@MainActivity,"service_enable",true)
                ContextCompat.startForegroundService(this, serviceIntent)
                bindService(intent, serviceConnection, BIND_AUTO_CREATE)

                isService = true
            }
        }
        binding.btnServiceStop.setOnClickListener {
            if(isService){
                SharedPreference.putSharedPreference(this@MainActivity,"service_enable",false)
                stopService(serviceIntent)
//                unbindService(serviceConnection)
                isService = false
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        var isEnableService = SharedPreference.getBooleanSharedPreference(this@MainActivity,"service_enable")

        if(isEnableService){
            stopService(serviceIntent)
            ContextCompat.startForegroundService(this, serviceIntent)
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        }else{
            stopService(serviceIntent)
//            unbindService(serviceConnection)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.handlePermissionsResult(requestCode, grantResults)
    }

    override fun onPermissionsGranted() {
        // 모든 권한이 허용되었을 때 처리할 로직
//        startPostureService()
    }

    override fun onPermissionsDenied() {
        // 권한이 거부된 경우 처리할 로직
        Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    private fun startPostureService() {
        // 서비스 시작 로직
    }
}