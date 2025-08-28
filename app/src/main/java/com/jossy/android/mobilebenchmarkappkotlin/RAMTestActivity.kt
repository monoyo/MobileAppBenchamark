package com.jossy.android.mobilebenchmarkappkotlin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult

class RAMTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)

        Log.d("RAMTestActivity", "Starting RAM test")
        startRAMTest()
    }

    private fun startRAMTest() {
        val ramStart = System.currentTimeMillis()
        RAMTest.runBenchmark()
        val ramElapsed = System.currentTimeMillis() - ramStart

        Log.i("RAMTestActivity", "RAM test time: ${ramElapsed}ms")
        val textView = TextView(this)
        textView.text = "Test RAM ended. Time: $ramElapsed ms"
        setContentView(textView)

        val result = TestResult("RAM Test", ramElapsed, "Memory allocation test completed", true)
        val intent = Intent()
        intent.putExtra(BenchmarkApplication.RESULT, result)
        setResult(RESULT_OK, intent)
        finish()
    }
}
