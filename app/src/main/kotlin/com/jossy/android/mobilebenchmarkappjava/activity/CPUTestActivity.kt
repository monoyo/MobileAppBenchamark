package com.jossy.android.mobilebenchmarkappjava.activity

import com.jossy.android.mobilebenchmarkappjava.CPUTest
import com.jossy.android.mobilebenchmarkappjava.R
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestResult

class CPUTestActivity : BaseTestActivity() {

    override fun initializeActivity() {
        setContentView(R.layout.activity_cpu_test)
        statusText = findViewById(R.id.cpuStatus)
    }

    override fun executeBenchmark() {
        initializeCsvWriter()
        repeat(Config.samplesAmount) { i ->
            executeSingleIteration(i)
        }
        csvWriter?.flush()
    }

    private fun executeSingleIteration(index: Int) {
        val start = System.currentTimeMillis()
        val result = CPUTest.runBenchmarkIterations(Config.samplesAmount.toLong(), null)
        val duration = System.currentTimeMillis() - start

        val testResult = TestResult(
            testName = "CPU Test",
            executionTime = duration,
            details = "threads=${result.threads}",
            success = true
        )
        logTestResult(index, testResult)
        updateProgress(index + 1)
    }

    override fun getProgressDisplayText(currentIteration: Int): String =
        "CPU Test: $currentIteration / ${Config.samplesAmount}"

    override fun getTestName(): String = "CPU Test"
}
