package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.LocationBenchmarkTest
import com.jossy.android.mobilebenchmarkappkotlin.LocationTestResult
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import kotlinx.coroutines.launch

class LocationTestActivity : ComponentActivity() {
    private lateinit var locationTest: LocationBenchmarkTest

    private val locationPermissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val hasLocationPermissions = permissions.entries.all { it.value }
            if (hasLocationPermissions) {
                startSingleLocationTest()
            } else {
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_test)
        locationTest = LocationBenchmarkTest(this)
        requestLocationPermissions()
    }

    private fun requestLocationPermissions() {
        locationPermissionRequest.launch(REQUIRED_PERMISSIONS)
    }

    private fun startSingleLocationTest() {
        lifecycleScope.launch {
            val result = locationTest.getCurrentLocation()
            sendResultAndFinish(result)
        }
    }

    private fun sendResultAndFinish(result: LocationTestResult) {
        val benchmarkResult =
            TestResult(
                testName = "Location Test",
                executionTime = result.elapsedTimeMs,
                details = "Accuracy: ${result.accuracy}m, Provider: ${result.provider}",
                isSuccessful = true,
            )
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(BenchmarkApplication.RESULT, benchmarkResult)
            },
        )
        finish()
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
    }
}
