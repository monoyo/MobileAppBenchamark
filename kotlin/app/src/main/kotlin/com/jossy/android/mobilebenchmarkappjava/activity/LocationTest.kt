package com.jossy.android.mobilebenchmarkappjava.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry
import com.jossy.android.mobilebenchmarkappjava.data.TestResult
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter
import java.io.File
import java.util.Locale

class LocationTest : AppCompatActivity() {
    companion object {
        private const val TAG = "LocationTest"
        private const val WATCHDOG_TIMEOUT_MS = 15_000L
        private const val UI_UPDATE_FREQUENCY = 50
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var csvWriter: BufferedCsvWriter? = null

    private lateinit var statusText: TextView
    private lateinit var metricsText: TextView
    private lateinit var progressBar: ProgressBar

    private var sampleCount = 0
    private var intervalMs = 1000L
    private var csvPath: String? = null
    private var suiteStartTime = 0L
    private var lastSampleTime = 0L
    private var isFinished = false
    private var lastKnownLocation: Location? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdogHandler = Handler(Looper.getMainLooper())

    private val samplingRunnable = object : Runnable {
        override fun run() {
            if (isFinished) return
            performSample()
            mainHandler.postDelayed(this, intervalMs)
        }
    }

    private val watchdogRunnable = {
        if (!isFinished) {
            Log.e(TAG, "Watchdog: No location updates received for ${WATCHDOG_TIMEOUT_MS}ms")
            logErrorToCsv()
            finishBenchmark()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initViews()
        parseIntent()
        initCsv()
        initLocationClient()
        startBenchmark()
    }

    private fun initViews() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }
        
        statusText = TextView(this).apply {
            text = "Location Test: Starting..."
            textSize = 16f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(statusText)
        
        metricsText = TextView(this).apply {
            text = "System metrics disabled"
            textSize = 14f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        layout.addView(metricsText)
        
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        layout.addView(progressBar)
        
        setContentView(layout)
    }

    private fun parseIntent() {
        // Use 1ms interval for high-speed sampling in "Stream+Poll" mode
        intervalMs = intent.getIntExtra("interval", 1).toLong()
        if (intervalMs > 100) {
            // If legacy interval was passed, override it to 1ms for standardization
            intervalMs = 1L
        }
        
        csvPath = intent.getStringExtra("csv_path")
        progressBar.max = Config.sampleCount
    }

    private fun initCsv() {
        suiteStartTime = System.currentTimeMillis()
        lastSampleTime = suiteStartTime
        val path = csvPath ?: "${filesDir}/location_benchmark_${suiteStartTime}.csv"
        try {
            csvWriter = BufferedCsvWriter(File(path), Config.bufferSize, 128 * 1024).apply { initialize() }
        } catch (e: Exception) {
            Log.e(TAG, "CSV Writer initialization failed", e)
        }
    }

    private fun initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isFinished) return
                locationResult.lastLocation?.let {
                    lastKnownLocation = it
                    resetWatchdog()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBenchmark() {
        Log.i(TAG, "Starting Benchmark: ${Config.sampleCount} samples @ ${intervalMs}ms")

        val locationRequest = LocationRequest.Builder(1000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(500)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        mainHandler.postDelayed(samplingRunnable, intervalMs)
        resetWatchdog()
    }

    private fun performSample() {
        try {
            val now = System.currentTimeMillis()
            val entry = createTestEntry(now)
            csvWriter?.write(entry)

            sampleCount++

            if (sampleCount % UI_UPDATE_FREQUENCY == 0 || sampleCount >= Config.sampleCount) {
                updateUI()
            }

            if (sampleCount >= Config.sampleCount) {
                finishBenchmark()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sampling", e)
        }
    }

    private fun createTestEntry(timestamp: Long): TestEntry {
        val duration = timestamp - lastSampleTime
        lastSampleTime = timestamp

        val details = lastKnownLocation?.let {
            String.format(
                Locale.US,
                "Lat:%.6f,Lon:%.6f,Acc:%.1f",
                it.latitude, it.longitude, it.accuracy
            )
        } ?: "No Signal"

        val result = TestResult("Location Test", duration, details, lastKnownLocation != null)
        return TestEntry(sampleCount, result, timestamp, duration, timestamp - suiteStartTime)
    }

    private fun updateUI() {
        runOnUiThread {
            statusText.text = String.format(Locale.US, "Progress: %d / %d", sampleCount, Config.sampleCount)
            progressBar.progress = sampleCount
            metricsText.text = "System metrics disabled"
        }
    }

    private fun logErrorToCsv() {
        csvWriter?.let {
            val now = System.currentTimeMillis()
            val duration = now - lastSampleTime
            lastSampleTime = now
            val tr = TestResult("Location Test", duration, "Error: Watchdog Timeout (No GPS Signal)", false)
            val entry = TestEntry(sampleCount, tr, now, duration, now - suiteStartTime)
            it.write(entry)
        }
    }

    private fun resetWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable)
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)
    }

    private fun finishBenchmark() {
        if (isFinished) return
        isFinished = true

        cleanup()

        val totalTime = System.currentTimeMillis() - suiteStartTime
        val result = TestResult(
            "Location Test",
            totalTime,
            "Completed $sampleCount samples",
            sampleCount > 0
        )

        Intent().apply {
            putExtra(BenchmarkApplication.RESULT, result)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    private fun cleanup() {
        watchdogHandler.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        csvWriter?.close()
    }

    override fun onPause() {
        super.onPause()
        if (!isFinished && !isChangingConfigurations()) {
            finishBenchmark()
        }
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
