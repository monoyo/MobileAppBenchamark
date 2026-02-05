package com.jossy.android.mobilebenchmarkappjava.activity

import android.util.Log
import com.jossy.android.mobilebenchmarkappjava.R
import com.jossy.android.mobilebenchmarkappjava.RAMTest
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestResult

class RAMTestActivity : BaseTestActivity() {

    companion object {
        private const val TAG = "RAMTestActivity"
    }

    override fun initializeActivity() {
        setContentView(R.layout.activity_ram_test)
        statusText = findViewById(R.id.cpuStatus)
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
        getString(R.string.ram_test_progress, currentIteration, Config.samplesAmount)

    override fun getTestName(): String = "RAM Test"
}
