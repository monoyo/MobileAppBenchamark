package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.RAMTest
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RAMTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ram_test)

        Log.d("RAMTestActivity", "Starting RAM test")
        startRAMTest()
    }

    private fun startRAMTest() {
        lifecycleScope.launch(Dispatchers.IO) {
            val ramStart = System.currentTimeMillis()
            RAMTest.runBenchmark()
            val ramElapsed = System.currentTimeMillis() - ramStart

            withContext(Dispatchers.Main) {
                val result = TestResult("RAM Test", ramElapsed, "Memory allocation test completed", true)
                val intent = Intent()
                intent.putExtra(BenchmarkApplication.RESULT, result)
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }
}
