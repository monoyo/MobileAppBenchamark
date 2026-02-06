package com.jossy.android.mobilebenchmarkappjava.activity

import android.content.Intent
import android.util.Log
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.Post
import com.jossy.android.mobilebenchmarkappjava.data.TestResult
import com.jossy.android.mobilebenchmarkappjava.service.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiTestActivity : BaseTestActivity() {

    companion object {
        private const val TAG = "ApiTestActivity"
    }

    private lateinit var apiService: ApiService

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
            text = "API Test: Starting..."
            textSize = 18f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(statusText)
        setContentView(layout)
        setupRetrofit()
    }

    override fun executeBenchmark() {
        initializeCsvWriter()
        startBatchTest()
    }

    private fun setupRetrofit() {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    private fun startBatchTest() {
        currentSampleIndex = 0
        makeApiRequest()
    }

    private fun makeApiRequest() {
        if (currentSampleIndex >= Config.samplesAmount) {
            finishBatch()
            return
        }

        val start = System.currentTimeMillis()
        apiService.getPosts().enqueue(ApiCallback(start, currentSampleIndex))
        currentSampleIndex++
    }

    private inner class ApiCallback(
        private val startTime: Long,
        private val iteration: Int
    ) : Callback<List<Post>> {

        override fun onResponse(call: Call<List<Post>>, response: Response<List<Post>>) {
            val success = response.isSuccessful && response.body() != null
            recordResult(success, if (success) "Success" else "Error: ${response.code()}")
        }

        override fun onFailure(call: Call<List<Post>>, t: Throwable) {
            recordResult(false, "Failure: ${t.message}")
        }

        private fun recordResult(success: Boolean, details: String) {
            val duration = System.currentTimeMillis() - startTime

            try {
                val result = TestResult("API Test", duration, details, success)
                logTestResult(iteration, result)
                updateProgress(iteration + 1)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record result: ${e.message}", e)
            }

            makeApiRequest()
        }
    }

    private fun finishBatch() {
        closeCsvWriter()
        val totalTime = System.currentTimeMillis() - suiteStartTime
        val result = TestResult(
            testName = "API Test",
            executionTime = totalTime,
            details = "Batch completed: $currentSampleIndex",
            success = true
        )
        runOnUiThread {
            Intent().apply {
                putExtra(BenchmarkApplication.RESULT, result)
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    override fun getProgressDisplayText(currentIteration: Int): String =
        "API Test: $currentIteration / ${Config.samplesAmount}"

    override fun getTestName(): String = "API Test"
}
