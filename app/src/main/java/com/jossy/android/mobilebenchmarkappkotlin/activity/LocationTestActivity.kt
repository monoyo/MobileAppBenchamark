package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.LocationBenchmarkTest
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import kotlinx.coroutines.launch

class LocationTestActivity : ComponentActivity() {
    private lateinit var locationTest: LocationBenchmarkTest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_test)
        locationTest = LocationBenchmarkTest(this)
        startSingleLocationTest()
    }

    private fun startSingleLocationTest() {
        lifecycleScope.launch {
            val result = locationTest.getCurrentLocation()
            sendResultAndFinish(result)
        }
    }

    private fun sendResultAndFinish(result: TestResult) {
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(BenchmarkApplication.RESULT, result)
            },
        )
        finish()
    }
}
