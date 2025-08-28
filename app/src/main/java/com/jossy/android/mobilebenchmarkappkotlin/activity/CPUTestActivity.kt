package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.CPUTest
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult

class CPUTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)

        Log.d("CPUTestActivity", "Starting CPU test")
        startCPUTest()
    }

    private fun startCPUTest() {
        val cpuStart = System.currentTimeMillis()
        for (i in 0 until 7) Thread { CPUTest.runBenchmark() }.start()
        CPUTest.runBenchmark()
        val cpuElapsed = System.currentTimeMillis() - cpuStart

        Log.i("CPUTestActivity", "CPU test time: ${cpuElapsed}ms")
        val textView = TextView(this)
        textView.text = "Test CPU ended. Time: ${cpuElapsed} ms"
        setContentView(textView)

        val result = TestResult("CPU Test", cpuElapsed, "CPU intensive operations completed", true)
        val intent = Intent()
        intent.putExtra(BenchmarkApplication.RESULT, result)
        setResult(RESULT_OK, intent)
        finish()
    }
}
