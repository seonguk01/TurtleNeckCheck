package com.turtle.turtleneckcheckgit.dialog

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.content.ContextCompat
import com.turtle.turtleneckcheckgit.R
import java.lang.Exception

class CommonAlertDialog : Dialog{
    var _isOk = false
    private var ctx: Context? = null
    private var tvContent: TextView? = null
    private var tvTopTitle: TextView? = null
    private var llayoutForTitle: LinearLayout? = null
    private var viewPaddingTop: View? = null
    private var viewPaddingBtm: View? = null
    private var btnCancel: Button? = null
    private var btnOk: Button? = null
    private var btnClose: RelativeLayout? = null
    private var tvMsg: TextView? = null
    //기본 : 상단 상태바 Default, Show Animation True

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val win = window
        val winLp = win!!.attributes
        winLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        win.attributes = winLp
    }

    constructor(context : Context ) : super(context,android.R.style.Theme_Translucent_NoTitleBar){
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        ctx = context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window!!.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val color = ContextCompat.getColor(context,R.color.translate)
                window!!.statusBarColor = color
            } else {
                val color = context.resources.getColor(R.color.translate)
                window!!.statusBarColor = color
            }
        }

        setView()
    }



    private fun setView( ){
        try {
            val win = window
            val winLp = win!!.attributes
            winLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            win.attributes = winLp
            setContentView(R.layout.dialog_comon_alert)
            btnCancel = findViewById(R.id.btnCancel)
            btnOk = findViewById(R.id.btnOk)
            btnClose = findViewById(R.id.btnClose)

            btnClose!!.setOnClickListener(View.OnClickListener {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    _isOk = false
                    cancel()
                }, 300)
            })

            btnCancel!!.setOnClickListener(View.OnClickListener {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    _isOk = false
                    cancel()
                }, 300)
            })
            btnOk!!.setOnClickListener(View.OnClickListener {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    _isOk = true
                    cancel()
                }, 300)
            })
        } catch (e: Throwable) {
            e.printStackTrace()
            cancel()
        }
    }
    fun setShowCancelBtn(isShow: Boolean) {
        if (isShow) {
            btnCancel!!.visibility = View.VISIBLE
        } else {
            btnCancel!!.visibility = View.GONE
        }
    }

    fun setTitleColor(color : Int ) {
        tvTopTitle!!.setTextColor(color);
    }

    fun setMessageStringFromHtml(html : String) {
        tvContent!!.setText(Html.fromHtml(html));
    }


}