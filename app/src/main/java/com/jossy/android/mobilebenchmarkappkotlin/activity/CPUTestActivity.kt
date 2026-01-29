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
    
    companion object {
        private const val DEFAULT_DURATION_MS = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cpu_test)

        Log.d("CPUTestActivity", "Starting CPU test")
        startCPUTest()
    }

    private fun startCPUTest() {
        val cpuIterations = intent.getLongExtra("cpu_iterations", 0)
        val useIterationMode = cpuIterations > 0
        
        lifecycleScope.launch(Dispatchers.Default) {
            val cpuStart = System.currentTimeMillis()
            
            val r = if (useIterationMode) {
                Log.d("CPUTestActivity", "Using iteration mode: $cpuIterations iterations")
                CPUTest.runBenchmarkIterations(totalIterations = cpuIterations)
            } else {
                Log.d("CPUTestActivity", "Using time mode: ${DEFAULT_DURATION_MS}ms")
                CPUTest.runBenchmarkParallel(durationMs = DEFAULT_DURATION_MS)
            }
            
            val cpuElapsed = System.currentTimeMillis() - cpuStart

            Log.i("CPUTestActivity", "CPU test time: ${cpuElapsed}ms, threads=${r.threads}, iters=${r.iterations}")

            withContext(Dispatchers.Main) {
                val result = TestResult(
                    "CPU Test",
                    cpuElapsed,
                    "threads=${r.threads}, iterations=${r.iterations}",
                    true
                )
                val intent = Intent()
                intent.putExtra(BenchmarkApplication.RESULT, result)
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }
}

