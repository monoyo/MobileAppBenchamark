package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.io.BufferedCsvWriter
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import java.io.File

class LocationTestActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var sampleCount: Int = 10000
    private var outputFile: String? = null
    private var csvWriter: BufferedCsvWriter? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var samplesCollected = 0
    private var startTimeMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    
    private var lastLocationTime = 0L
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - lastLocationTime > 15000) { // 15s watchdog
                finishBenchmark(false, "Watchdog timeout - no signal")
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_test)
        
        statusText = findViewById(R.id.locationStatus)

        sampleCount = intent.getIntExtra("sample_count", 10000)
        outputFile = intent.getStringExtra("output_file")
        
        initializeWriter()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        startLocationUpdates()
    }

    private fun initializeWriter() {
        outputFile?.let { path ->
            csvWriter = BufferedCsvWriter(File(path), 1000, 64 * 1024)
            csvWriter?.initialize()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finishBenchmark(false, "No permission")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500)
            .build()
            
        startTimeMs = System.currentTimeMillis()
        lastLocationTime = startTimeMs

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (samplesCollected >= sampleCount) return
                
                for (location in locationResult.locations) {
                    val now = System.currentTimeMillis()
                    lastLocationTime = now
                    samplesCollected++

                    csvWriter?.write(
                        iteration = samplesCollected,
                        executionTimeMs = 0, // Instant
                        details = "lat=${location.latitude}|lon=${location.longitude}|acc=${location.accuracy}",
                        intervalStartMs = now,
                        intervalDurationMs = 0,
                        cumulativeTimeMs = now - startTimeMs
                    )
                    
                    if (samplesCollected % 10 == 0) {
                        statusText.text = "Samples: $samplesCollected/$sampleCount"
                    }

                    if (samplesCollected >= sampleCount) {
                        finishBenchmark(true, "Completed $samplesCollected samples")
                        return
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        handler.postDelayed(watchdogRunnable, 1000)
    }
    
    private fun finishBenchmark(success: Boolean, msg: String) {
        handler.removeCallbacks(watchdogRunnable)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        csvWriter?.close()
        
        val totalTime = System.currentTimeMillis() - startTimeMs
        val result = TestResult(
            "Location Test",
            totalTime,
            msg,
            success
        )
        val intent = Intent()
        intent.putExtra(BenchmarkApplication.RESULT, result)
        setResult(RESULT_OK, intent)
        finish()
    }
}
