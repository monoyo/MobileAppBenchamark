package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.util.Log
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import com.jossy.android.mobilebenchmarkappkotlin.consts.Config
import com.jossy.android.mobilebenchmarkappkotlin.data.Post
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import com.jossy.android.mobilebenchmarkappkotlin.service.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ApiTest : BaseTestActivity() {

    companion object {
        private const val TAG = "ApiTest"
    }

    private lateinit var apiService: ApiService
    private val completionLatch = CountDownLatch(1)

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
        Log.d(TAG, "Starting batch test, sampleCount=${Config.sampleCount}")
        startBatchTest()
        Log.d(TAG, "Awaiting completionLatch...")
        // Wait indefinitely for completion, or up to 30 minutes if preferred.
        // Changed from await(30, TimeUnit.MINUTES) to await() for indefinite wait.
        completionLatch.await()
        Log.d(TAG, "Latch released.")
        csvWriter?.flush()
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
        if (currentSampleIndex >= Config.sampleCount) {
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
            Log.d(TAG, "recordResult iteration=$iteration, duration=$duration, success=$success")

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
        Log.d(TAG, "finishBatch called, countDown latch. currentSampleIndex=$currentSampleIndex")
        completionLatch.countDown()
    }

    override fun getProgressDisplayText(currentIteration: Int): String =
        "API Test: $currentIteration / ${Config.sampleCount}"

    override fun getTestName(): String = "API Test"
}
