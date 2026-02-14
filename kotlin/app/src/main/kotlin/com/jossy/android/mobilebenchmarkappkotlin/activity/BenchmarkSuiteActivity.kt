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
import com.jossy.android.mobilebenchmarkappkotlin.consts.Config
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import com.jossy.android.mobilebenchmarkappkotlin.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aktywność orkiestrująca wykonanie suite'a testów wydajnościowych.
 *
 * Optymalizacje dla dużych zbiorów danych (do 1M próbek):
 * 1. Buforowany zapis CSV (BufferedCsvWriter) - eliminuje wąskie gardło I/O
 * 2. Ring buffer dla podglądu w UI - ogranicza zużycie pamięci
 * 3. Asynchroniczne zapisywanie - nie blokuje pomiarów
 * 4. Checkpointy - odzyskiwanie danych przy błędach
 * 5. Konfigurowalny rozmiar próbek (100 do 1M)
 */
class BenchmarkSuiteActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BenchmarkSuiteActivity"
        private const val BENCHMARK_TAG = "BENCHMARK"
        private const val PERMISSION_REQUEST_CODE = 123
        private const val ALL_TESTS = 6
        private const val TEST_ACTIVITY_REQUEST_CODE = 456
    }

    private lateinit var currentTestInfo: TextView
    private lateinit var testResults: TextView
    private lateinit var testProgress: ProgressBar
    private lateinit var startTestsButton: Button
    private lateinit var exportResultsButton: Button
    
    // CheckBoxes
    private lateinit var testSelectionContainer: android.view.View
    private lateinit var checkUi: android.widget.CheckBox
    private lateinit var checkCpu: android.widget.CheckBox
    private lateinit var checkRam: android.widget.CheckBox
    private lateinit var checkImage: android.widget.CheckBox
    private lateinit var checkApi: android.widget.CheckBox
    private lateinit var checkLocation: android.widget.CheckBox

    private var currentIteration = 0
    private var currentTestIndex = 0
    private var isRunning = false
    private var testSuiteStartTime = 0L
    private var currentIterationStartTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var outputDir: File? = null
    private var sessionTimestamp: String? = null

    private var totalSamplesCollected = 0
    private var errorsEncountered = 0

    private val appStartTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark_suite)
        initViews()
        setupButtons()

        savedInstanceState?.let { restoreState(it) }

        val launchTime = System.currentTimeMillis() - appStartTime
        currentTestInfo.text = String.format(
            Locale.US,
            "App Launched in: %dms\nReady to start tests.", launchTime
        )

        Log.d(TAG, "onCreate completed, UI initialized")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putBoolean("isRunning", isRunning)
            putInt("currentTestIndex", currentTestIndex)
            putInt("currentIteration", currentIteration)
            putInt("totalSamplesCollected", totalSamplesCollected)
            putInt("errorsEncountered", errorsEncountered)
            putString("sessionTimestamp", sessionTimestamp)
        }
        Log.i(TAG, "State saved")
    }

    private fun restoreState(savedState: Bundle) {
        isRunning = savedState.getBoolean("isRunning")
        currentTestIndex = savedState.getInt("currentTestIndex")
        currentIteration = savedState.getInt("currentIteration")
        totalSamplesCollected = savedState.getInt("totalSamplesCollected")
        errorsEncountered = savedState.getInt("errorsEncountered")
        sessionTimestamp = savedState.getString("sessionTimestamp")

        if (isRunning && sessionTimestamp != null) {
            try {
                restoreSession()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore session", e)
                isRunning = false
                Toast.makeText(this, "Failed to restore session: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // Restore UI state
        if (isRunning) {
            testSelectionContainer.visibility = android.view.View.GONE
            startTestsButton.isEnabled = false
            startTestsButton.text = "Uruchamianie..."
        }
        
        updateProgressMax()
        updateProgress()
    }

    private fun restoreSession() {
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        outputDir = File(baseDir, "benchmarks/$sessionTimestamp").apply {
            if (!exists()) mkdirs()
        }
        Log.i(TAG, "Session restored: ${outputDir?.absolutePath}")
    }

    private fun initViews() {
        currentTestInfo = findViewById(R.id.currentTestInfo)
        testResults = findViewById(R.id.testResults)
        testProgress = findViewById(R.id.testProgress)
        startTestsButton = findViewById(R.id.startTestsButton)
        exportResultsButton = findViewById(R.id.exportResultsButton)
        
        testSelectionContainer = findViewById(R.id.testSelectionContainer)
        checkUi = findViewById(R.id.checkUi)
        checkCpu = findViewById(R.id.checkCpu)
        checkRam = findViewById(R.id.checkRam)
        checkImage = findViewById(R.id.checkImage)
        checkApi = findViewById(R.id.checkApi)
        checkLocation = findViewById(R.id.checkLocation)
    }

    private fun setupButtons() {
        startTestsButton.setOnClickListener {
            if (!isRunning) startTestSuite()
        }
        exportResultsButton.setOnClickListener { exportResults() }
    }

    private fun updateProgressMax() {
        testProgress.max = Config.sampleCount * ALL_TESTS
    }

    private fun startTestSuite() {
        // Validation: Check if at least one test is selected
        if (!isAnyTestSelected()) {
            Toast.makeText(this, "Please select at least one test", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        try {
            initializeSession()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot initialize output: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to initialize session", e)
            return
        }

        isRunning = true
        currentIteration = 0
        currentTestIndex = 0
        totalSamplesCollected = 0
        errorsEncountered = 0
        testSuiteStartTime = System.currentTimeMillis()

        testResults.text = ""
        startTestsButton.apply {
            text = "Uruchamianie..."
            isEnabled = false
        }
        
        // Hide selection, show running state
        testSelectionContainer.visibility = android.view.View.GONE
        
        updateProgressMax()

        Log.i(TAG, "Starting test suite")
        runNextTest()
    }
    
    private fun isAnyTestSelected(): Boolean {
        return checkUi.isChecked || checkCpu.isChecked || checkRam.isChecked || 
               checkImage.isChecked || checkApi.isChecked || checkLocation.isChecked
    }
    
    private fun isTestSelected(index: Int): Boolean {
        return when (index) {
            0 -> checkUi.isChecked
            1 -> checkCpu.isChecked
            2 -> checkRam.isChecked
            3 -> checkImage.isChecked
            4 -> checkApi.isChecked
            5 -> checkLocation.isChecked
            else -> false
        }
    }

    private fun initializeSession() {
        sessionTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        outputDir = File(baseDir, "benchmarks/$sessionTimestamp").apply {
            if (!exists() && !mkdirs()) {
                throw Exception("Cannot create output directory: $absolutePath")
            }
        }
        Log.i(TAG, "Session initialized: ${outputDir?.absolutePath}")
    }

    private fun runNextTest() {
        // Skip unselected tests
        while (currentTestIndex < ALL_TESTS && !isTestSelected(currentTestIndex)) {
            currentTestIndex++
        }
        
        if (currentTestIndex < ALL_TESTS) {
            val testName = getTestName(currentTestIndex)
            onTestStarted(testName)
            currentIterationStartTime = System.currentTimeMillis()
            startSpecificTest(currentTestIndex)
        } else {
            onAllTestsCompleted()
        }
    }
    
    // ... getTestName and startSpecificTest remain same ...

    // ... onTestCompleted ...

    // Restore onAllTestsCompleted to show selection again

    
    // ... rest of the file ...

    private fun getTestName(index: Int) = when (index) {
        0 -> "UI Stress Test"
        1 -> "CPU Test"
        2 -> "RAM Test"
        3 -> "Image Loading Test"
        4 -> "API Test"
        5 -> "Location Test"
        else -> "Unknown Test"
    }

    private fun startSpecificTest(index: Int) {
        if (outputDir == null) {
            Log.e(TAG, "outputDir is null in startSpecificTest. Attempting to restore or aborting.")
            if (sessionTimestamp != null) {
                try {
                    restoreSession()
                } catch (e: Exception) {
                    Log.e(TAG, "Recovery failed", e)
                    Toast.makeText(this, "Test failed: Session lost", Toast.LENGTH_LONG).show()
                    isRunning = false
                    startTestsButton.isEnabled = true
                    return
                }
            } else {
                Toast.makeText(this, "Test failed: outputDir is null", Toast.LENGTH_SHORT).show()
                isRunning = false
                startTestsButton.isEnabled = true
                return
            }
        }

        val intent = when (index) {
            0 -> Intent(this, UiTest::class.java).apply {
                putExtra("session_dir", outputDir?.absolutePath)
            }
            1 -> Intent(this, CpuTest::class.java)
            2 -> Intent(this, RamTest::class.java)
            3 -> Intent(this, ImageLoadingTest::class.java)
            4 -> Intent(this, ApiTest::class.java)
            else -> Intent(this, LocationTest::class.java).apply {
                putExtra("interval", Config.sampleCount)
            }
        }

        intent.putExtra("auto_mode", true)

        val testName = getTestName(index)
        val safeName = testName.lowercase(Locale.US).replace(" ", "_")
        val csvFile = File(outputDir, "$safeName.csv")
        intent.putExtra("csv_path", csvFile.absolutePath)

        startActivityForResult(intent, TEST_ACTIVITY_REQUEST_CODE)
    }

    fun onTestCompleted(result: TestResult) {
        val duration = System.currentTimeMillis() - currentIterationStartTime
        Log.i(TAG, "Test ${result.testName} completed in ${duration}ms")

        if (result.success) {
            totalSamplesCollected += Config.sampleCount
        }

        currentTestIndex++
        handler.postDelayed(::runNextTest, 500)
    }

    fun onAllTestsCompleted() {
        isRunning = false
        startTestsButton.apply {
            text = "Start Tests"
            isEnabled = true
        }

        // Generate summary.csv with aggregated statistics
        outputDir?.let { dir ->
            try {
                com.jossy.android.mobilebenchmarkappkotlin.utils.SummaryWriter.writeSummary(dir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write summary", e)
            }
        }

        val totalTime = System.currentTimeMillis() - testSuiteStartTime
        val summary = String.format(
            Locale.US,
            "All tests completed!\nTotal samples: %d\nErrors: %d\nTotal time: %.2fs\nOutput: %s",
            totalSamplesCollected,
            errorsEncountered,
            totalTime / 1000.0,
            outputDir?.absolutePath ?: "N/A"
        )
        currentTestInfo.text = summary

        Log.i(TAG, summary.replace("\n", ", "))
    }

    fun onTestStarted(testName: String) {
        Log.i(BENCHMARK_TAG, "TEST_START:$testName")
        currentTestInfo.text = String.format(Locale.US, "Running: %s", testName)
    }

    private fun updateProgress() {
        val progress = (currentTestIndex + 1) * Config.sampleCount
        testProgress.progress = progress
    }

    private fun exportResults() {
        if (outputDir == null || !outputDir!!.exists()) {
            Toast.makeText(this, "No results to export yet", Toast.LENGTH_SHORT).show()
            return
        }

        val files = outputDir!!.listFiles { _, name -> name.endsWith(".csv") }
        val count = files?.size ?: 0

        if (count > 0) {
            Toast.makeText(
                this,
                String.format(Locale.US, "Results saved: %d files in %s", count, outputDir?.absolutePath),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "No CSV files found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        startTestSuite()
                    } else {
                        Toast.makeText(this, "Permissions required to run tests", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            TEST_ACTIVITY_REQUEST_CODE -> {
                if (resultCode == RESULT_OK && data != null) {
                    try {
                        @Suppress("DEPRECATION")
                        val result = data.getSerializableExtra(BenchmarkApplication.RESULT) as? TestResult
                        if (result != null) {
                            onTestCompleted(result)
                        } else {
                            Log.e(TAG, "No TestResult in intent")
                            errorsEncountered++
                            handler.postDelayed(::runNextTest, 500)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing activity result: ${e.message}", e)
                        errorsEncountered++
                        handler.postDelayed(::runNextTest, 500)
                    }
                } else {
                    errorsEncountered++
                    Log.w(TAG, "Test activity returned with error or cancel")
                    handler.postDelayed(::runNextTest, 500)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
