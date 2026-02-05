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
import com.jossy.android.mobilebenchmarkappkotlin.service.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ApiTestActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var sampleCount: Int = 10000
    private var outputFile: String? = null
    private var csvWriter: BufferedCsvWriter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_test)
        
        statusText = findViewById(R.id.apiStatus)

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
        lifecycleScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var totalDuration = 0L

            for (i in 0 until sampleCount) {
                val loopStart = System.currentTimeMillis()
                
                try {
                    ApiService.fetchPosts()
                } catch (e: Exception) {
                    Log.e("ApiTest", "Request failed $i", e)
                }

                val loopEnd = System.currentTimeMillis()
                val duration = loopEnd - loopStart
                totalDuration += duration

                csvWriter?.write(
                    iteration = i,
                    executionTimeMs = duration,
                    details = "GET /posts",
                    intervalStartMs = loopStart,
                    intervalDurationMs = duration,
                    cumulativeTimeMs = loopEnd - startTime
                )
            }

            csvWriter?.close()

            withContext(Dispatchers.Main) {
                val result = TestResult(
                    "API Test",
                    System.currentTimeMillis() - startTime,
                    "Completed $sampleCount requests",
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
