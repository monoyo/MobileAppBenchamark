package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult

class BenchmarkSuiteActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val TEST_ITERATIONS = 3
        private const val TEST_ACTIVITY_REQUEST_CODE = 456
    }

    private lateinit var currentTestInfo: TextView
    private lateinit var testResults: TextView
    private lateinit var testProgress: ProgressBar
    private lateinit var startTestsButton: Button
    private lateinit var exportResultsButton: Button

    private val allResults = mutableListOf<TestResult>()
    private var currentIteration = 0
    private var currentTestIndex = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var resultBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark_suite)

        currentTestInfo = findViewById(R.id.currentTestInfo)
        testResults = findViewById(R.id.testResults)
        testProgress = findViewById(R.id.testProgress)
        startTestsButton = findViewById(R.id.startTestsButton)
        exportResultsButton = findViewById(R.id.exportResultsButton)

        startTestsButton.setOnClickListener {
            if (!isRunning) startTestSuite()
        }

        exportResultsButton.setOnClickListener { exportResults() }

        testProgress.max = TEST_ITERATIONS * 4
        Log.d("BenchmarkSuiteActivity", "onCreate completed, UI initialized")
    }

    private fun startTestSuite() {
        if (checkPermissions()) {
            isRunning = true
            currentIteration = 0
            currentTestIndex = 0
            allResults.clear()
            resultBuilder = StringBuilder()
            testResults.text = ""
            startTestsButton.text = "Running..."
            startTestsButton.isEnabled = false
            runNextTest()
        } else {
            requestPermissions()
        }
    }

    private fun runNextTest() {
        if (currentIteration < TEST_ITERATIONS) {
            if (currentTestIndex < 6) {
                val testName = getTestName(currentTestIndex)
                onTestStarted(testName)
                startSpecificTest(currentTestIndex)
                currentTestIndex++
            } else {
                currentTestIndex = 0
                currentIteration++
                if (currentIteration < TEST_ITERATIONS) runNextTest()
            }
        } else {
            onAllTestsCompleted()
        }
    }

    private fun getTestName(index: Int): String =
        when (index) {
            0 -> "UI Test"
            1 -> "CPU Test"
            2 -> "RAM Test"
            3 -> "Image Loading Test"
            4 -> "API Test"
            5 -> "Location Test"
            else -> "Unknown Test"
        }

    private fun startSpecificTest(index: Int) {
        val intent =
            when (index) {
                0 -> Intent(this, UITestActivity::class.java)
                1 -> Intent(this, CPUTestActivity::class.java)
                2 -> Intent(this, RAMTestActivity::class.java)
                3 -> Intent(this, ImageLoadingActivity::class.java)
                4 -> Intent(this, ApiTestActivity::class.java)
                5 -> Intent(this, LocationTestActivity::class.java)
                else -> return
            }
        intent.putExtra("auto_mode", true)
        intent.putExtra("callback_activity", BenchmarkSuiteActivity::class.java.name)
        startActivityForResult(intent, TEST_ACTIVITY_REQUEST_CODE)
    }

    fun onTestCompleted(result: TestResult) {
        allResults.add(result)
        updateProgress()
        appendResult(result)
        handler.postDelayed({ runNextTest() }, 1000)
    }

    private fun onAllTestsCompleted() {
        isRunning = false
        startTestsButton.text = "Start Tests"
        startTestsButton.isEnabled = true
        currentTestInfo.text = "All tests completed!"
        calculateAndDisplayAverages()
    }

    private fun onTestStarted(testName: String) {
        currentTestInfo.text = "Running: $testName (Iteration ${currentIteration + 1}/$TEST_ITERATIONS)"
    }

    private fun updateProgress() {
        val progress = (currentIteration * 4) + currentTestIndex
        testProgress.progress = progress
    }

    private fun appendResult(result: TestResult) {
        val resultText = "[Iteration ${currentIteration + 1}] ${result.testName}: ${result.executionTime}ms - ${result.details}\n"
        resultBuilder.append(resultText)
        testResults.text = resultBuilder.toString()
    }

    private fun calculateAndDisplayAverages() {
        val averages = StringBuilder("\nAverage Results:\n")
        for (i in 0..3) {
            val testName = getTestName(i)
            var sum: Long = 0
            var count = 0
            for (result in allResults) {
                if (result.testName == testName) {
                    sum += result.executionTime
                    count++
                }
            }
            if (count > 0) {
                val avg = sum.toDouble() / count
                averages.append("$testName Average: ${"%.2f".format(avg).replace(',', '.')}ms\n")
            }
        }
        resultBuilder.append(averages)
        testResults.text = resultBuilder.toString()
    }

    private fun exportResults() {
        try {
            val timestamp = System.currentTimeMillis().toString()
            val fileName = "benchmark_results_$timestamp.txt"

            openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                output.write(resultBuilder.toString().encodeToByteArray())
            }

            Toast.makeText(this, "Results exported to $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error exporting results: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startTestSuite()
                } else {
                    Toast
                        .makeText(
                            this,
                            "Permissions required to run tests",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        } else if (requestCode == TEST_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            val result = data?.getSerializableExtra(BenchmarkApplication.RESULT) as? TestResult
            if (result != null) onTestCompleted(result) else Log.e("BenchmarkSuiteActivity", "No TestResult received from test activity")
        }
    }
}
