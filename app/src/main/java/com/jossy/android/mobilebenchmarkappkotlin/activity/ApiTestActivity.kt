package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import com.jossy.android.mobilebenchmarkappkotlin.service.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ApiTestActivity : AppCompatActivity() {
    private lateinit var statusTextView: TextView
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ApiTestActivity", "onCreate called")
        setContentView(R.layout.activity_api_test)
        statusTextView = findViewById(R.id.apiStatus)
        startApiTest()
    }

    private fun startApiTest() {
        Log.d("ApiTestActivity", "Starting API test (Ktor)")
        startTime = System.currentTimeMillis()
        statusTextView.text = "Starting API request..."

        lifecycleScope.launch {
            try {
                val posts = ApiService.fetchPosts()
                val totalTime = System.currentTimeMillis() - startTime
                Log.d("ApiTestActivity", "API test completed in ${totalTime}ms items=${posts.size}")
                val result = TestResult("API Test", totalTime, "API request completed successfully (count=${posts.size})", true)
                val intent = Intent()
                intent.putExtra(BenchmarkApplication.RESULT, result)
                setResult(RESULT_OK, intent)
                finish()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                statusTextView.text = "Error: ${t.message}"
                Log.e("ApiTestActivity", "API error", t)
            }
        }
    }
}
