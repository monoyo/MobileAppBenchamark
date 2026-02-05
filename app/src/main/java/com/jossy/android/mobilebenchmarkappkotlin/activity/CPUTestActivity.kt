package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.io.BufferedCsvWriter
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import com.jossy.android.mobilebenchmarkappkotlin.test.CPUTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CPUTestActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var sampleCount: Int = 10000
    private var cpuIterations: Long = 1_000_000L
    private var outputFile: String? = null
    private var csvWriter: BufferedCsvWriter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cpu_test)
        
        statusText = findViewById(R.id.cpuStatus)
        
        sampleCount = intent.getIntExtra("sample_count", 10000)
        cpuIterations = intent.getLongExtra("cpu_iterations", 1_000_000L)
        outputFile = intent.getStringExtra("output_file")

        initializeWriter()

        startTestLoop()
    }

    private fun initializeWriter() {
        outputFile?.let { path ->
            csvWriter = BufferedCsvWriter(File(path), 1000, 64 * 1024)
            csvWriter?.initialize()
        }
    }

    private fun startTestLoop() {
        lifecycleScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            var totalDuration = 0L

            for (i in 0 until sampleCount) {
                val blockStart = System.currentTimeMillis()
                
                // Run one block
                val result = CPUTest.runBenchmarkIterations(cpuIterations)
                
                val blockEnd = System.currentTimeMillis()
                val duration = blockEnd - blockStart
                totalDuration += duration

                // Log
                csvWriter?.write(
                    iteration = i,
                    executionTimeMs = duration,
                    details = "checksum=${result.checksum}|threads=${result.threads}",
                    intervalStartMs = blockStart,
                    intervalDurationMs = duration,
                    cumulativeTimeMs = blockEnd - startTime
                )

                // Update UI occasionally
                if (i % 100 == 0) {
                    withContext(Dispatchers.Main) {
                        Log.d("CPUTest", "Sample $i/$sampleCount")
                    }
                }
            }

            // Finish
            csvWriter?.close()
            
            withContext(Dispatchers.Main) {
                val result = TestResult(
                    "CPU Test",
                    System.currentTimeMillis() - startTime,
                    "Completed $sampleCount samples",
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
