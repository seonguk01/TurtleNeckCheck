package com.turtle.turtleneckcheckgit

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.font.FontVariation
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import com.turtle.turtleneckcheckgit.common.ActionIntent
import com.turtle.turtleneckcheckgit.databinding.ActivityMainBinding
import com.turtle.turtleneckcheckgit.dialog.CommonAlertDialog
import com.turtle.turtleneckcheckgit.module.PermissionCheckerManager
import com.turtle.turtleneckcheckgit.service.PostureMonitoringService
import handasoft.mobile.divination.module.pref.SharedPreference

class MainActivity : AppCompatActivity(), PermissionCheckerManager.PermissionListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionHelper: PermissionCheckerManager
    var  isService : Boolean= false;
    var isPermission : Boolean = false
    private var postureMonitoringService : PostureMonitoringService ?= null
    private var  isBound = false
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    /*private  val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PostureMonitoringService.LocalBinder
            Log.d("FaceDetection", "onServiceConnected: ")
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
            Log.d("FaceDetection", "onServiceDisconnected: ")

            isBound = false
        }

    }*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.FOREGROUND_SERVICE_CAMERA,
            Manifest.permission.POST_NOTIFICATIONS,


        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionHelper = PermissionCheckerManager(this, requiredPermissions.toTypedArray(), 100)
        permissionHelper.requestPermissionsIfNecessary()
        val serviceIntent = Intent(this, PostureMonitoringService::class.java)

        // ActivityResultLauncher 초기화
        //다른앱 위에 띄우기 권한 획득을 위한 로직
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    // 권한이 부여된 경우 팝업을 띄움
                } else {
                    // 권한이 부여되지 않은 경우 처리
                    showPermissionDeniedDialog()
                }
            }
        }

        binding.btnServiceStart.setOnClickListener {
            if(!isService){
//                stopService(serviceIntent)
                SharedPreference.putSharedPreference(this@MainActivity,"service_enable",true)
                ContextCompat.startForegroundService(this, serviceIntent)
//                bindService(intent, serviceConnection, BIND_AUTO_CREATE)

                isService = true
            }
        }
        binding.btnServiceStop.setOnClickListener {
            if(isService){
                SharedPreference.putSharedPreference(this@MainActivity,"service_enable",false)
                ContextCompat.startForegroundService(this, serviceIntent)

//                stopSettingForegroundService()
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
            SharedPreference.putSharedPreference(this@MainActivity,"service_enable",true)
            ContextCompat.startForegroundService(this, serviceIntent)
//            bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        }else{
            SharedPreference.putSharedPreference(this@MainActivity,"service_enable",false)
            ContextCompat.startForegroundService(this, serviceIntent)
//            unbindService(serviceConnection)
        }
    }

    private fun stopSettingForegroundService() {
        try{
            var action =  ActionIntent.STOPFOREGROUND_ACTION
            Log.d("handa_log", "stopForegroundService: ${action}")

            val startIntent = Intent(this, PostureMonitoringService::class.java)
            intent.putExtra("action",action)
            startIntent.action = action
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }catch (e : Throwable){
            e.printStackTrace()
        }
        SharedPreference.putSharedPreference(this@MainActivity,"service_enable",false)

    }

    private fun startSettingForegroundService() {

        try{
            var action = ActionIntent.STARTFOREGROUND_ACTION
            Log.d("handa_log", "startForegroundService: $action")

            val startIntent = Intent(this, PostureMonitoringService::class.java)
            intent.putExtra("action",action)
            startIntent.action = action
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ){

                ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),4000)

                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
            SharedPreference.putSharedPreference(this@MainActivity,"service_enable",true)

        }catch (e : Throwable){
            e.printStackTrace()
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
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(!Settings.canDrawOverlays(this@MainActivity)){
                //권한이 없으면 설정 화면으로 이동
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, 1000)

                overlayPermissionLauncher = registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.canDrawOverlays(this)) {
                            // 권한이 부여된 경우 팝업을 띄움
                            Toast.makeText(this, "권한 설정이 완료되었습니다.", Toast.LENGTH_LONG).show()

                        } else {
                            // 권한이 부여되지 않은 경우 처리
                            showPermissionDeniedDialog()
                        }
                    }
                }
            }
        }
        isPermission = true
    }
    private fun showPermissionDeniedDialog() {
        try {
            val alert = CommonAlertDialog(
                this@MainActivity,
            )
            alert.setShowCancelBtn(false)
            alert.show()
        } catch (e: Exception) {
        }

    }
    override fun onPermissionsDenied() {
        // 권한이 거부된 경우 처리할 로직
        Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        isPermission = false
    }

    private fun startPostureService() {
        // 서비스 시작 로직
    }
}