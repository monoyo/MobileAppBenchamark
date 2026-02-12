package com.jossy.android.mobilebenchmarkappjava.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry
import com.jossy.android.mobilebenchmarkappjava.data.TestResult
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter
import java.io.File
import java.util.concurrent.Executors

abstract class BaseTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BaseTestActivity"
    }

    protected lateinit var statusText: TextView
    protected val progressLiveData = MutableLiveData<Int>()
    protected var csvWriter: BufferedCsvWriter? = null
    protected var suiteStartTime = 0L
    protected var csvPath: String? = null
    protected var currentSampleIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeActivity()

        progressLiveData.observe(this) { updateProgressText(it) }

        csvPath = intent.getStringExtra("csv_path")
        Log.d(TAG, "Test activity initialized")

        startBenchmarkAsync()
    }

    protected abstract fun initializeActivity()

    protected abstract fun executeBenchmark()

    protected abstract fun getProgressDisplayText(currentIteration: Int): String

    protected abstract fun getTestName(): String

    protected fun startBenchmarkAsync() {
        Executors.newSingleThreadExecutor().execute(this::runBenchmarkTask)
    }

    private fun runBenchmarkTask() {
        suiteStartTime = System.currentTimeMillis()
        try {
            executeBenchmark()
            handleSuccess(System.currentTimeMillis() - suiteStartTime)
        } catch (e: Exception) {
            handleError(e)
        }
    }

    protected fun logTestResult(iteration: Int, result: TestResult) {
        try {
            if (csvWriter == null) {
                initializeCsvWriter()
            }

            val start = System.currentTimeMillis()
            val duration = result.executionTimeMs
            val elapsed = System.currentTimeMillis() - suiteStartTime

            val entry = TestEntry(iteration, result, start, duration, elapsed)
            csvWriter?.write(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log result: ${e.message}", e)
        }
    }

    protected fun initializeCsvWriter() {
        if (csvWriter != null) return

        val file = File(csvPath ?: getDefaultCsvPath())
        csvWriter = BufferedCsvWriter(file, 1000, 64 * 1024).apply { initialize() }
    }

    protected fun getDefaultCsvPath(): String =
        "${filesDir.absolutePath}/benchmark_results.csv"

    protected fun updateProgress(currentIteration: Int) {
        progressLiveData.postValue(currentIteration)
    }

    private fun updateProgressText(currentIteration: Int) {
        statusText.text = getProgressDisplayText(currentIteration)
    }

    protected fun handleSuccess(totalTime: Long) {
        Log.i(TAG, "Benchmark completed in ${totalTime}ms")
        runOnUiThread {
            val result = TestResult(
                testName = getTestName(),
                executionTimeMs = totalTime,
                details = "Batch completed: ${Config.sampleCount} samples",
                success = true
            )
            closeCsvWriter()
            returnResult(result)
        }
    }

    protected fun handleError(e: Exception) {
        Log.e(TAG, "Benchmark failed: ${e.message}", e)
        runOnUiThread {
            val result = TestResult(
                testName = getTestName(),
                executionTimeMs = 0,
                details = "Error: ${e.message}",
                success = false
            )
            closeCsvWriter()
            returnResult(result)
        }
    }

    protected fun closeCsvWriter() {
        try {
            csvWriter?.apply {
                flush()
                close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing CSV writer: ${e.message}", e)
        }
    }

    private fun returnResult(result: TestResult) {
        Intent().apply {
            putExtra(BenchmarkApplication.RESULT, result)
            setResult(RESULT_OK, this)
        }
        finish()
    }
}
