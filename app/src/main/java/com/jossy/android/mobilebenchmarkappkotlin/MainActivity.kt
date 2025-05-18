package com.jossy.android.mobilebenchmarkappkotlin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var startupTimeView: TextView
    private lateinit var executionTimeView: TextView
    private lateinit var ramUsageView: TextView
    private lateinit var cpuUsageView: TextView
    private lateinit var uiLatencyView: TextView

    private var startupTime: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startTime = SystemClock.elapsedRealtime()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Inicjalizacja widoków
        startupTimeView = findViewById(R.id.startupTime)
        executionTimeView = findViewById(R.id.executionTime)
        ramUsageView = findViewById(R.id.ramUsage)
        cpuUsageView = findViewById(R.id.cpuUsage)
        uiLatencyView = findViewById(R.id.uiLatency)

        // Pomiar czasu uruchomienia
        val loadedTime = SystemClock.elapsedRealtime()
        startupTime = loadedTime - startTime
        startupTimeView.text = "Czas uruchomienia: ${startupTime}ms"

        // Przypisanie przycisków
        findViewById<Button>(R.id.btnExecution).setOnClickListener {
            measureExecutionTime()
        }

        findViewById<Button>(R.id.btnSimulateUsage).setOnClickListener {
            simulateRamCpuUsage()
        }

        findViewById<Button>(R.id.btnUILatency).setOnClickListener {
            measureUILatency()
        }
    }

    private fun measureExecutionTime() {
        val start = SystemClock.elapsedRealtime()
        for (i in 0..100_000) {
            val x = i.toDouble()
            val result = sqrt(x * x + x) * ln(x + 1)
        }
        val end = SystemClock.elapsedRealtime()
        val duration = end - start
        executionTimeView.text = "Czas wykonania operacji: ${duration}ms"
    }

    private fun simulateRamCpuUsage() {
        val ram = Random.nextDouble(100.0, 200.0)
        val cpu = Random.nextDouble(10.0, 60.0)
        ramUsageView.text = "Zużycie RAM: %.1f MB".format(ram)
        cpuUsageView.text = "Zużycie CPU: %.1f %%".format(cpu)
    }

    private fun measureUILatency() {
        val start = SystemClock.uptimeMillis()
        Handler(Looper.getMainLooper()).post {
            val end = SystemClock.uptimeMillis()
            val latency = end - start
            uiLatencyView.text = "Latencja UI: ${latency}ms"
        }
    }
}