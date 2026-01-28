package com.jossy.android.mobilebenchmarkappkotlin.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import com.jossy.android.mobilebenchmarkappkotlin.service.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ApiTestViewModel: ViewModel() {
    private val dispatcher = Dispatchers.IO
    private var testStartTime: Long = 0
    fun runTest(onEnd: (TestResult) -> Unit, onError: (String) -> Unit) {
        Log.d("ApiTestActivity", "Starting API test (Ktor)")
        testStartTime = System.currentTimeMillis()

        viewModelScope.launch(dispatcher) {
            try {
                val posts = ApiService.fetchPosts()
                val totalTime = System.currentTimeMillis() - testStartTime
                Log.d("ApiTestActivity", "API test completed in ${totalTime}ms items=${posts.size}")
                val result = TestResult("API Test", totalTime, "API request completed successfully (count=${posts.size})", true)
                onEnd(result)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onError("Error: ${t.message}")
                Log.e("ApiTestActivity", "API error", t)
            }
        }
    }

}