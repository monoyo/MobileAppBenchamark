package com.jossy.android.mobilebenchmarkappjava.activity

import android.util.Log
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import com.jossy.android.mobilebenchmarkappjava.RAMTest
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestResult

class RamTest : BaseTestActivity() {

    companion object {
        private const val TAG = "RamTest"
    }

    override fun initializeActivity() {
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
            text = "RAM Test: Starting..."
            textSize = 18f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(statusText)
        setContentView(layout)
    }

    override fun executeBenchmark() {
        initializeCsvWriter()
        val runsPerSample = calculateRunsPerSample()

        Log.i(TAG, "Starting RAM Batch: samples=${Config.samplesAmount}, runs/sample=$runsPerSample")

        repeat(Config.samplesAmount) { i ->
            executeSingleIteration(i, runsPerSample)
        }
        csvWriter?.flush()
    }

    private fun calculateRunsPerSample(): Int = 50

    private fun executeSingleIteration(index: Int, runsPerSample: Int) {
        val start = System.currentTimeMillis()
        RAMTest.runBenchmark(runsPerSample)
        val duration = System.currentTimeMillis() - start

        val result = TestResult(
            testName = "RAM Test",
            executionTime = duration,
            details = "RAM benchmark",
            success = true
        )
        logTestResult(index, result)
        updateProgress(index + 1)
    }

    override fun getProgressDisplayText(currentIteration: Int): String =
        "RAM Test: $currentIteration / ${Config.samplesAmount}"

    override fun getTestName(): String = "RAM Test"
}
