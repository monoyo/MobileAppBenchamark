package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.extensions.onEnd
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import com.jossy.android.mobilebenchmarkappkotlin.service.ApiService
import com.jossy.android.mobilebenchmarkappkotlin.viewmodel.ApiTestViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ApiTestActivity : AppCompatActivity() {
    private lateinit var statusTextView: TextView
    private val viewModel = ApiTestViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ApiTestActivity", "onCreate called")
        setContentView(R.layout.activity_api_test)
        statusTextView = findViewById(R.id.apiStatus)
        startApiTest()
    }

    private fun startApiTest() {
        viewModel.runTest( { result ->
            onEnd(result)
        }, { error ->
            onError(error)
        })
    }

    private fun onError(error: String) {
        statusTextView.text = error
    }
}
