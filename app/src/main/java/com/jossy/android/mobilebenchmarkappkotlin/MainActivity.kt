package com.jossy.android.mobilebenchmarkappkotlin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val startTime = System.currentTimeMillis()
    private var hasAppLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStartBenchmark).setOnClickListener {
            val intent = Intent(this, BenchmarkActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasAppLaunched) {
            hasAppLaunched = true
            reportFullyDrawn()
            val elapsedTime = System.currentTimeMillis() - startTime
            findViewById<TextView>(R.id.appLaunchTime)
                .text = "App Launch Time: ${elapsedTime}ms"
        }
    }
}
