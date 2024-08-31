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
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
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
                SharedPreference.putSharedPreference(this@MainActivity,"service_enable",true)
                serviceIntent.putExtra("action",ActionIntent.STARTFOREGROUND_ACTION)
                serviceIntent.action = ActionIntent.STARTFOREGROUND_ACTION;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService( serviceIntent)
                }else{
                    startService(serviceIntent)
                }

                isService = true
            }
        }
        binding.btnServiceStop.setOnClickListener {
            if(isService){
                serviceIntent.putExtra("action",ActionIntent.STOPFOREGROUND_ACTION)
                serviceIntent.action = ActionIntent.STOPFOREGROUND_ACTION;
                SharedPreference.putSharedPreference(this@MainActivity,"service_enable",false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService( serviceIntent)
                }else{
                    startService(serviceIntent)
                }
                isService = false
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        var isEnableService = SharedPreference.getBooleanSharedPreference(this@MainActivity,"service_enable")
        Log.e("handa_log", "service_enable: $isEnableService")

        if(isEnableService){
            SharedPreference.putSharedPreference(this@MainActivity,"service_enable",true)
            serviceIntent.putExtra("action",ActionIntent.STARTFOREGROUND_ACTION)
            serviceIntent.action = ActionIntent.STARTFOREGROUND_ACTION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService( serviceIntent)
            }else{
                startService(serviceIntent)
            }

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