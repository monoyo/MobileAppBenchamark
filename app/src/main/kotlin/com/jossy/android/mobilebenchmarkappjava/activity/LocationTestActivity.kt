package com.jossy.android.mobilebenchmarkappjava.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.jossy.android.mobilebenchmarkappjava.R
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry
import com.jossy.android.mobilebenchmarkappjava.data.TestResult
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter
import java.io.File
import java.util.Locale

class LocationTestActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "LocationTestActivity"
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
        setContentView(R.layout.activity_location_test)

        initViews()
        parseIntent()
        initCsv()
        initLocationClient()
        startBenchmark()
    }

    private fun initViews() {
        statusText = findViewById(R.id.locationStatus)
        metricsText = findViewById(R.id.metricsText)
        progressBar = findViewById(R.id.testProgress)
    }

    private fun parseIntent() {
        intervalMs = intent.getIntExtra("interval", 1000).toLong()
        csvPath = intent.getStringExtra("csv_path")
        progressBar.max = Config.samplesAmount
    }

    private fun initCsv() {
        suiteStartTime = System.currentTimeMillis()
        lastSampleTime = suiteStartTime
        val path = csvPath ?: "${filesDir}/location_benchmark_${suiteStartTime}.csv"
        try {
            csvWriter = BufferedCsvWriter(File(path), 500, 128 * 1024).apply { initialize() }
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
        Log.i(TAG, "Starting Benchmark: ${Config.samplesAmount} samples @ ${intervalMs}ms")

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

            if (sampleCount % UI_UPDATE_FREQUENCY == 0 || sampleCount >= Config.samplesAmount) {
                updateUI()
            }

            if (sampleCount >= Config.samplesAmount) {
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
            statusText.text = String.format(Locale.US, "Progress: %d / %d", sampleCount, Config.samplesAmount)
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
