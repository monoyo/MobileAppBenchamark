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
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.config.SampleConfiguration
import com.jossy.android.mobilebenchmarkappkotlin.io.BufferedCsvWriter
import com.jossy.android.mobilebenchmarkappkotlin.metrics.SystemMetricsCollector
import com.jossy.android.mobilebenchmarkappkotlin.model.TestEntry
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Aktywność orkiestrująca wykonanie suite'a testów wydajnościowych.
 * 
 * Optymalizacje dla dużych zbiorów danych (do 1M próbek):
 * 1. Buforowany zapis CSV (BufferedCsvWriter) - eliminuje wąskie gardło I/O
 * 2. Ring buffer dla podglądu w UI - ogranicza zużycie pamięci
 * 3. Asynchroniczne zapisywanie - nie blokuje pomiarów
 * 4. Checkpointy - odzyskiwanie danych przy błędach
 * 5. SystemMetricsCollector - zbiera CPU/RAM/GPU/FPS w tle
 */
class BenchmarkSuiteActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BenchmarkSuiteActivity"
        private const val BENCHMARK_TAG = "BENCHMARK"
        private const val PERMISSION_REQUEST_CODE = 123
        private const val ALL_TESTS = 6
        private const val TEST_ACTIVITY_REQUEST_CODE = 456
        private const val UI_DISPLAY_BUFFER_SIZE = 100
    }

    // UI Components
    private lateinit var currentTestInfo: TextView
    private lateinit var testResults: TextView
    private lateinit var testProgress: ProgressBar
    private lateinit var startTestsButton: Button
    private lateinit var exportResultsButton: Button
    private lateinit var sampleConfigSpinner: Spinner

    // Configuration
    private var selectedConfig = SampleConfiguration.SMALL

    // Test State
    private var currentIteration = 0
    private var currentTestIndex = 0
    private var isRunning = false
    private var testSuiteStartTime = 0L
    private var currentIterationStartTime = 0L

    // Data Storage - Optimized for large datasets
    private val csvWriters = linkedMapOf<String, BufferedCsvWriter>()
    private val uiDisplayBuffers = linkedMapOf<String, ArrayDeque<TestEntry>>()
    private val handler = Handler(Looper.getMainLooper())
    private var outputDir: File? = null
    private var sessionTimestamp: String = ""

    // Statistics
    private var totalSamplesCollected = 0
    private var errorsEncountered = 0

    // System Metrics Collector
    private var metricsCollector: SystemMetricsCollector? = null

    private val appStartTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark_suite)

        initViews()
        setupSampleConfigSpinner()

        val launchTime = System.currentTimeMillis() - appStartTime
        currentTestInfo.text = "App Launched in: ${launchTime}ms\nReady to start tests."
    }

    private fun initViews() {
        currentTestInfo = findViewById(R.id.currentTestInfo)
        testResults = findViewById(R.id.testResults)
        testProgress = findViewById(R.id.testProgress)
        startTestsButton = findViewById(R.id.startTestsButton)
        exportResultsButton = findViewById(R.id.exportResultsButton)
        sampleConfigSpinner = findViewById(R.id.sampleConfigSpinner)

        startTestsButton.setOnClickListener {
            if (!isRunning) startTestSuite()
        }

        exportResultsButton.setOnClickListener { 
            Toast.makeText(this, "Results are saved automatically during tests", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSampleConfigSpinner() {
        val configNames = SampleConfiguration.entries.map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, configNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sampleConfigSpinner.adapter = adapter

        sampleConfigSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedConfig = SampleConfiguration.fromOrdinal(position)
                updateProgressMax()
                Log.i(TAG, "Selected config: ${selectedConfig.displayName}")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateProgressMax() {
        testProgress.max = selectedConfig.sampleCount * ALL_TESTS
        testProgress.progress = 0
    }

    private fun startTestSuite() {
        if (checkPermissions()) {
            try {
                initializeSession()
                isRunning = true
                currentIteration = 0
                currentTestIndex = 0
                totalSamplesCollected = 0
                errorsEncountered = 0
                testSuiteStartTime = System.currentTimeMillis()

                startTestsButton.text = "Running..."
                startTestsButton.isEnabled = false
                sampleConfigSpinner.isEnabled = false
                testResults.text = ""

                runNextTest()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start test suite", e)
                Toast.makeText(this, "Failed to start: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            requestPermissions()
        }
    }

    private fun initializeSession() {
        sessionTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        outputDir = File(baseDir, "benchmarks/$sessionTimestamp").apply { mkdirs() }

        // Close previous writers
        closeWriters()
        csvWriters.clear()
        uiDisplayBuffers.clear()

        // Start system metrics collector
        metricsCollector?.stop()
        metricsCollector = SystemMetricsCollector(this, outputDir!!, sessionTimestamp)
        metricsCollector?.start()

        // Initialize writers for each test
        for (i in 0 until ALL_TESTS) {
            val testName = getTestName(i)
            val safeName = testName.lowercase(Locale.US).replace(" ", "_")
            val csvFile = File(outputDir, "$safeName.csv")

            val writer = BufferedCsvWriter(
                csvFile,
                selectedConfig.bufferSize,
                selectedConfig.optimalWriteBufferBytes
            )
            writer.initialize()
            csvWriters[testName] = writer

            uiDisplayBuffers[testName] = ArrayDeque(UI_DISPLAY_BUFFER_SIZE)
        }

        Log.i(TAG, "Session initialized: ${outputDir?.absolutePath}")
    }

    private fun runNextTest() {
        if (currentTestIndex < ALL_TESTS) {
            if (currentIteration < selectedConfig.sampleCount) {
                val testName = getTestName(currentTestIndex)
                currentIterationStartTime = System.currentTimeMillis()
                onTestStarted(testName)
                startSpecificTest(currentTestIndex)
            } else {
                currentTestIndex++
                currentIteration = 0
                if (currentTestIndex < ALL_TESTS) {
                    runNextTest()
                } else {
                    onAllTestsCompleted()
                }
            }
        } else {
            onAllTestsCompleted()
        }
    }

    private fun getTestName(index: Int): String = when (index) {
        0 -> "UI Test"
        1 -> "CPU Test"
        2 -> "RAM Test"
        3 -> "Image Loading Test"
        4 -> "API Test"
        5 -> "Location Test"
        else -> "Unknown Test"
    }

    private fun startSpecificTest(index: Int) {
        val intent = when (index) {
            0 -> Intent(this, UITestActivity::class.java)
            1 -> Intent(this, CPUTestActivity::class.java).apply {
                putExtra("cpu_iterations", selectedConfig.cpuIterationsPerThread)
            }
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
        try {
            val iterationEndTime = System.currentTimeMillis()
            val intervalDuration = iterationEndTime - currentIterationStartTime
            val cumulativeTime = iterationEndTime - testSuiteStartTime

            val entry = TestEntry(
                iteration = currentIteration,
                result = result,
                intervalStartMs = currentIterationStartTime,
                intervalDurationMs = intervalDuration,
                cumulativeTimeMs = cumulativeTime
            )

            // Write to buffered CSV
            csvWriters[result.testName]?.write(
                iteration = currentIteration,
                executionTimeMs = result.executionTime,
                details = result.details,
                intervalStartMs = currentIterationStartTime,
                intervalDurationMs = intervalDuration,
                cumulativeTimeMs = cumulativeTime
            )

            // Update UI display buffer (ring buffer)
            uiDisplayBuffers[result.testName]?.let { buffer ->
                if (buffer.size >= UI_DISPLAY_BUFFER_SIZE) {
                    buffer.removeFirst()
                }
                buffer.addLast(entry)
            }

            totalSamplesCollected++
            currentIteration++
            updateProgress()
            updateCsvDisplay()

            // Log end marker
            Log.i(BENCHMARK_TAG, "TEST_END:${result.testName}")

        } catch (e: Exception) {
            errorsEncountered++
            Log.e(TAG, "Error processing result: ${e.message}", e)
            saveAllCheckpoints()
        }

        // Continue with next test
        handler.postDelayed({ runNextTest() }, selectedConfig.testDelayMs)
    }

    private fun onAllTestsCompleted() {
        isRunning = false
        startTestsButton.text = "Start Tests"
        startTestsButton.isEnabled = true
        sampleConfigSpinner.isEnabled = true

        closeWriters()
        metricsCollector?.stop()

        val totalTime = System.currentTimeMillis() - testSuiteStartTime
        val summary = String.format(Locale.US,
            "All tests completed!\n" +
                    "Total samples: %d\n" +
                    "Errors: %d\n" +
                    "Total time: %.2fs\n" +
                    "Output: %s",
            totalSamplesCollected,
            errorsEncountered,
            totalTime / 1000.0,
            outputDir?.absolutePath ?: "N/A")
        currentTestInfo.text = summary

        calculateAndDisplayAverages()
    }

    private fun onTestStarted(testName: String) {
        // Sync metrics collector
        metricsCollector?.setCurrentTest(testName, currentIteration)

        // Log markers
        Log.i(BENCHMARK_TAG, "TEST_START:$testName")
        Log.i(BENCHMARK_TAG, "ITERATION:$currentIteration")

        currentTestInfo.text = "Running: $testName\n" +
                "Iteration ${currentIteration + 1}/${selectedConfig.sampleCount}\n" +
                "Total collected: $totalSamplesCollected"
    }

    private fun updateProgress() {
        val progress = (currentTestIndex * selectedConfig.sampleCount) + currentIteration
        testProgress.progress = progress
    }

    private fun updateCsvDisplay() {
        val sb = StringBuilder()
        uiDisplayBuffers.forEach { (testName, entries) ->
            if (entries.isNotEmpty()) {
                sb.appendLine("# $testName (last ${entries.size})")
                entries.toList().takeLast(5).forEach { e ->
                    sb.appendLine("  [${e.iteration}] ${e.result.executionTime}ms")
                }
            }
        }
        testResults.text = sb.toString()
    }

    private fun calculateAndDisplayAverages() {
        val averages = StringBuilder("\nAverages:\n")
        uiDisplayBuffers.forEach { (testName, entries) ->
            if (entries.isNotEmpty()) {
                val avg = entries.map { it.result.executionTime }.average()
                averages.append("$testName: ${"%.2f".format(Locale.US, avg)}ms\n")
            }
        }
        testResults.append(averages.toString())
    }

    private fun closeWriters() {
        csvWriters.values.forEach { writer ->
            try {
                writer.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing writer: ${e.message}")
            }
        }
    }

    private fun saveAllCheckpoints() {
        csvWriters.values.forEach { writer ->
            try {
                writer.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Checkpoint save error: ${e.message}")
            }
        }
    }

    private fun checkPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        startTestSuite()
                    } else {
                        Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            TEST_ACTIVITY_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    @Suppress("DEPRECATION")
                    val result = data?.getSerializableExtra(BenchmarkApplication.RESULT) as? TestResult
                    if (result != null) {
                        onTestCompleted(result)
                    } else {
                        Log.e(TAG, "No TestResult received")
                        errorsEncountered++
                        handler.postDelayed({ runNextTest() }, 100)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        metricsCollector?.stop()
        closeWriters()
    }
}
