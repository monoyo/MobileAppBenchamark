package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.jossy.android.mobilebenchmarkappkotlin.data.TestEntry
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BenchmarkSuiteActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val TEST_ITERATIONS = 30
        private const val ALL_TESTS = 6
        private const val TEST_ACTIVITY_REQUEST_CODE = 456
    }

    private lateinit var currentTestInfo: TextView
    private lateinit var testResults: TextView
    private lateinit var testProgress: ProgressBar
    private lateinit var startTestsButton: Button
    private lateinit var exportResultsButton: Button

    private val allResults = mutableListOf<TestResult>()
    private val perTestResults = linkedMapOf<String, MutableList<TestEntry>>()
    private var currentIteration = 0
    private var currentTestIndex = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var resultBuilder = StringBuilder()
    private val testStartTime = System.currentTimeMillis()

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

        testProgress.max = TEST_ITERATIONS * ALL_TESTS
        Log.d("BenchmarkSuiteActivity", "onCreate completed, UI initialized")
        val launchTime = System.currentTimeMillis() - testStartTime
        currentTestInfo.append("App Launched in: ")
        currentTestInfo.append(launchTime.toString())
        currentTestInfo.append("ms \nReady to start tests.")
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
        if (currentTestIndex < ALL_TESTS) {
            if (currentIteration < TEST_ITERATIONS) {
                val testName = getTestName(currentTestIndex)
                onTestStarted(testName)
                startSpecificTest(currentTestIndex)
                currentIteration++
            } else {
                currentTestIndex++
                currentIteration = 0
                if (currentTestIndex < ALL_TESTS) runNextTest()
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

    private fun onTestCompleted(result: TestResult) {
        allResults.add(result)
        val iterationNum = currentIteration
        val entry = TestEntry(iteration = iterationNum, result = result)
        val list = perTestResults.getOrPut(result.testName) { mutableListOf() }
        list.add(entry)
        updateProgress()
        updateCsvDisplay()
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
        val progress = (currentTestIndex * TEST_ITERATIONS) + currentIteration
        testProgress.progress = progress
    }

    private fun updateCsvDisplay() {
        val sb = StringBuilder()
        perTestResults.forEach { (testName, entries) ->
            sb.appendLine("# ${testName}")
            sb.appendLine("iteration,executionTimeMs,details,success")
            entries.sortedBy { it.iteration }.forEach { e ->
                sb.appendLine(listOf(
                    e.iteration.toString(),
                    e.result.executionTime.toString(),
                    csv(e.result.details),
                    e.result.isSuccessful.toString()
                ).joinToString(","))
            }
            sb.appendLine()
        }
        testResults.text = sb.toString()
        resultBuilder = sb
    }

    private fun calculateAndDisplayAverages() {
        val averages = StringBuilder("\nAverages:\n")
        perTestResults.forEach { (testName, entries) ->
            if (entries.isNotEmpty()) {
                val avg = entries.map { it.result.executionTime }.average()
                averages.append("$testName: ${"%.2f".format(Locale.US, avg)}ms\n")
            }
        }
        resultBuilder.append(averages)
        testResults.text = resultBuilder.toString()
    }

    private fun exportResults() {
        try {
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
            val baseDir: File? = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val outDir = File(baseDir, "benchmarks").apply { mkdirs() }

            if (!outDir.exists()) {
                Toast.makeText(this, "Cannot access output directory", Toast.LENGTH_SHORT).show()
                return
            }

            var filesCount = 0
            perTestResults.forEach { (testName, entries) ->
                if (entries.isEmpty()) return@forEach

                val safeName = testName.lowercase(Locale.US).replace(" ", "_")
                val file = File(outDir, "${safeName}_${time}.csv")
                val content = buildCsv(entries)
                file.writeText(content)
                filesCount++
            }

            if (filesCount > 0) {
                Toast.makeText(this, "Saved $filesCount CSV files to ${outDir.absolutePath}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "No results to export yet", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildCsv(entries: List<TestEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("iteration,executionTimeMs,details,success")
        entries.sortedBy { it.iteration }.forEach { e ->
            sb.appendLine(listOf(
                e.iteration.toString(),
                e.result.executionTime.toString(),
                csv(e.result.details),
                e.result.isSuccessful.toString()
            ).joinToString(","))
        }
        return sb.toString()
    }

    private fun csv(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
