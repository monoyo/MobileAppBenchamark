package com.jossy.android.mobilebenchmarkappkotlin

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val startTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btnStartBenchmark).setOnClickListener {
            performTests()
        }
    }

    override fun onResume() {
        super.onResume()
        reportFullyDrawn()
        val elapsedTime = System.currentTimeMillis() - startTime
        findViewById<TextView>(R.id.appLaunchTime)
            .text = "App Launch Time: ${elapsedTime}ms"
    }

    private fun performTests() {
    }
}
