package com.jossy.android.mobilebenchmarkappjava.activity

import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import com.jossy.android.mobilebenchmarkappjava.CPUTest
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestResult

class CpuTest : BaseTestActivity() {

    override fun initializeActivity() {
        // Create layout programmatically (no R resource needed)
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
            text = "CPU Test: Starting..."
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
        repeat(Config.sampleCount) { i ->
            executeSingleIteration(i)
        }
        csvWriter?.flush()
    }

    private fun executeSingleIteration(index: Int) {
        val start = System.currentTimeMillis()
        val result = CPUTest.runBenchmarkIterations(Config.cpuIterations.toLong(), null)
        val duration = System.currentTimeMillis() - start

        val testResult = TestResult(
            testName = "CPU Test",
            executionTimeMs = duration,
            details = "threads=${result.threads}",
            success = true
        )
        logTestResult(index, testResult)
        updateProgress(index + 1)
    }

    override fun getProgressDisplayText(currentIteration: Int): String =
        "CPU Test: $currentIteration / ${Config.sampleCount}"

    override fun getTestName(): String = "CPU Test"
}
