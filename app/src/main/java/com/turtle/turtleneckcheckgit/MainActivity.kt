package com.turtle.turtleneckcheckgit

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.turtle.turtleneckcheckgit.databinding.ActivityMainBinding
import com.turtle.turtleneckcheckgit.module.PermissionCheckerManager
import com.turtle.turtleneckcheckgit.service.PostureMonitoringService

class MainActivity : AppCompatActivity(), PermissionCheckerManager.PermissionListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionHelper: PermissionCheckerManager
    var  isService : Boolean= false;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionHelper = PermissionCheckerManager(this, requiredPermissions.toTypedArray(), 100)
        permissionHelper.requestPermissionsIfNecessary()
        val serviceIntent = Intent(this, PostureMonitoringService::class.java)

        binding.btnServiceStart.setOnClickListener {
            if(!isService){
                ContextCompat.startForegroundService(this, serviceIntent)
                isService = true
            }
        }
        binding.btnServiceStop.setOnClickListener {
            if(isService){
                stopService(serviceIntent)
                isService = false
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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