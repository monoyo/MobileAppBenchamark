package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.R

class MainActivity : AppCompatActivity() {
    private val startTime = System.currentTimeMillis()
    private var hasAppLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")
        setContentView(R.layout.activity_main)

        val startSuiteButton: Button = findViewById(R.id.btnStartSuite)
        startSuiteButton.setOnClickListener {
            val intent = Intent(this@MainActivity, BenchmarkSuiteActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasAppLaunched) {
            hasAppLaunched = true
            val elapsedTime = System.currentTimeMillis() - startTime
            val launchTime: TextView = findViewById(R.id.appLaunchTime)
            launchTime.text = "App Launch Time: ${elapsedTime}ms"
        }
    }
}
