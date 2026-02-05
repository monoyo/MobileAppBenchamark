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
import com.jossy.android.mobilebenchmarkappkotlin.test.RAMTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RAMTestActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var sampleCount: Int = 10000
    private var outputFile: String? = null
    private var csvWriter: BufferedCsvWriter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ram_test)
        
        statusText = findViewById(R.id.ramStatus)
        
        sampleCount = intent.getIntExtra("sample_count", 10000)
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
                val cycleStart = System.currentTimeMillis()
                
                RAMTest.runBenchmarkCycle(i)
                
                val cycleEnd = System.currentTimeMillis()
                val duration = cycleEnd - cycleStart
                totalDuration += duration

                csvWriter?.write(
                    iteration = i,
                    executionTimeMs = duration,
                    details = "RAM Cycle",
                    intervalStartMs = cycleStart,
                    intervalDurationMs = duration,
                    cumulativeTimeMs = cycleEnd - startTime
                )

                if (i % 100 == 0) {
                    withContext(Dispatchers.Main) {
                        Log.d("RAMTest", "Sample $i/$sampleCount")
                    }
                }
            }

            csvWriter?.close()

            withContext(Dispatchers.Main) {
                val result = TestResult(
                    "RAM Test",
                    System.currentTimeMillis() - startTime,
                    "Completed $sampleCount cycles",
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
