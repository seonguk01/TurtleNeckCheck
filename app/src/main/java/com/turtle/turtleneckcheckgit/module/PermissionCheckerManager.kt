package com.turtle.turtleneckcheckgit.module

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionCheckerManager (private val activity: AppCompatActivity,
                                private val requiredPermissions: Array<String>,
                                private val requestCode: Int){


    fun allPermissionsGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissionsIfNecessary() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                activity,
                requiredPermissions,
                requestCode
            )
        } else {
            // 모든 권한이 이미 허용된 경우 처리할 로직
            (activity as? PermissionListener)?.onPermissionsGranted()
        }
    }

    fun handlePermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ) {
        if (this.requestCode == requestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 권한이 모두 허용되었을 때
                (activity as? PermissionListener)?.onPermissionsGranted()
            } else {
                // 권한이 거부된 경우 처리할 로직
                (activity as? PermissionListener)?.onPermissionsDenied()
            }
        }
    }

    interface PermissionListener {
        fun onPermissionsGranted()
        fun onPermissionsDenied()
    }
}