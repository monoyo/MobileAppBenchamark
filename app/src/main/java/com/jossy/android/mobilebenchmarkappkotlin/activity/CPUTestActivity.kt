package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.CPUTest
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CPUTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cpu_test)

        Log.d("CPUTestActivity", "Starting CPU test")
        startCPUTest()
    }

    private fun startCPUTest() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cpuStart = System.currentTimeMillis()
            for (i in 0 until 7) CPUTest.runBenchmark()
            val cpuElapsed = System.currentTimeMillis() - cpuStart

            Log.i("CPUTestActivity", "CPU test time: ${cpuElapsed}ms")

            withContext(Dispatchers.Main) {
                val result =
                    TestResult(
                        "CPU Test",
                        cpuElapsed,
                        "CPU intensive operations completed",
                        true,
                    )
                val intent = Intent()
                intent.putExtra(BenchmarkApplication.RESULT, result)
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }
}
