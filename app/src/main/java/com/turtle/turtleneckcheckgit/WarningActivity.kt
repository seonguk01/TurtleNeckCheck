package com.turtle.turtleneckcheckgit

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.turtle.turtleneckcheckgit.databinding.ActivityMainBinding
import com.turtle.turtleneckcheckgit.databinding.ActivityWarningBinding

class WarningActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWarningBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR

        binding = ActivityWarningBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.button.setOnClickListener{
            finish()
        }

    }
}