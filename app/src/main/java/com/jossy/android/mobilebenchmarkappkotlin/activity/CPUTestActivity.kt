package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.test.CPUTest
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
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
        lifecycleScope.launch(Dispatchers.Default) {
            val cpuStart = System.currentTimeMillis()
            val r = CPUTest.runBenchmarkParallel(durationMs = 3000L)
            val cpuElapsed = System.currentTimeMillis() - cpuStart

            Log.i("CPUTestActivity", "CPU test time: ${cpuElapsed}ms, threads=${r.threads}, iters=${r.iterations}")

            withContext(Dispatchers.Main) {
                val result = TestResult(
                    "CPU Test",
                    cpuElapsed,
                    "threads=${ r.threads}, iterations=${r.iterations}",
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
