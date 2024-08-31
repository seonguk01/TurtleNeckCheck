package com.turtle.turtleneckcheckgit.receiver

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.turtle.turtleneckcheckgit.common.Constant
import com.turtle.turtleneckcheckgit.module.ServiceUtil
import handasoft.mobile.divination.module.pref.SharedPreference

class AlarmReceiverBoot : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let{
            intent?.let{
                if(intent!!.action == Intent.ACTION_BOOT_COMPLETED || intent!!.action == Intent.ACTION_LOCKED_BOOT_COMPLETED){

                    //킷켓 이상일 경우 반복알람을 사용하지 못함으로 노티가 올때 사용자가 설정한 시간으로 그 다음날 알람을 다시 설정함


                    if (SharedPreference.getBooleanSharedPreference(
                            context,
                            "service_enable"
                        )
                    ) {
                        ServiceUtil.startForegroundService(context)
                    }
                }
            }
        }
    }
}