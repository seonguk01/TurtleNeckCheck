package com.turtle.turtleneckcheckgit.module

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.turtle.turtleneckcheckgit.common.Constant
import com.turtle.turtleneckcheckgit.service.PostureMonitoringService
import com.turtle.turtleneckcheckgit.type.ServiceType
import handasoft.mobile.divination.module.pref.SharedPreference

object ServiceUtil {
    fun startForegroundService(context: Context) {
        val startIntent = Intent(context, PostureMonitoringService::class.java)
        startIntent.action = Constant.ACTION.STARTFOREGROUND_ACTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }
        SharedPreference.putSharedPreference(
            context,
            Constant.SERVICE_STATE,
            ServiceType.SERVICE_FORE
        )
    }

}