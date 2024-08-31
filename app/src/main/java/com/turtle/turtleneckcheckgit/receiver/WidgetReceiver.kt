package com.turtle.turtleneckcheckgit.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.turtle.turtleneckcheckgit.MainActivity

class WidgetReceiver : BroadcastReceiver()
{
    override fun onReceive(context: Context?, intent: Intent?) {
        val i = Intent(context, MainActivity::class.java)
        // VIEW화면이지만 홈키를 눌러 Foreground상태가 아닌경우 VIEW페이지 재활용 및 데이터 갱신을 위해.. CELAR_TOP사용
        //ction과 Category를 아래와 같이 추가하면 어떤 상황에서 알림(노티)을 누르더라도 앱의 마지막 스택 상태로 앱이 보여진다.
        //앱이 실행중일 때 알림(노티)를 누르면 그냥 아무런 실행없이 현재 상태의 화면이 유지되고, 실행 중 홈키로 나간 상태라면 홈키를 누르기 직전의 마지막 상태로 앱이 띄워진다.
        //
        //출처: https://like-tomato.tistory.com/156 [토마토의 일상 얘기]
        i.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        var pi : PendingIntent ?=  PendingIntent.getActivity(context,0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT  )
        try {
            pi?.send()
        } catch (e: Throwable ) {
            e.printStackTrace()
        }
    }
}