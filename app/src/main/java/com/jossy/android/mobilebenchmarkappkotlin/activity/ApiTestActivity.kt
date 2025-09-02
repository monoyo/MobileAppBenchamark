package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
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
        Log.d("ApiTestActivity", "Starting API test")
        startTime = System.currentTimeMillis()
        statusTextView.text = "Starting API request..."

        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BASIC

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val apiService = retrofit.create(ApiService::class.java)

        apiService.getPosts().enqueue(object : Callback<List<Post>> {
            override fun onResponse(call: Call<List<Post>>, response: Response<List<Post>>) {
                if (response.isSuccessful && response.body() != null) {
                    val totalTime = System.currentTimeMillis() - startTime
                    Log.d("ApiTestActivity", "API test completed in ${totalTime}ms")
                    val result = TestResult("API Test", totalTime, "API request completed successfully", true)
                    val intent = Intent()
                    intent.putExtra(BenchmarkApplication.RESULT, result)
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    statusTextView.text = "Error: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<List<Post>>, t: Throwable) {
                statusTextView.text = "Error: ${t.message}"
            }
        })
    }
}
