package com.turtle.turtleneckcheckgit.util

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.ContextCompat.getSystemService

object Util {
     fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName
        return appProcesses.any { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && it.processName == packageName }
    }
}