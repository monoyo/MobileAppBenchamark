package com.jossy.android.mobilebenchmarkappkotlin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val startTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val benchmarkStatus = findViewById<TextView>(R.id.benchmarkStatus)
        val editRuns = findViewById<EditText>(R.id.editRuns)
        val benchmarkLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val cpuAvg = result.data!!.getDoubleExtra("cpuAvg", 0.0)
                    val ramAvg = result.data!!.getDoubleExtra("ramAvg", 0.0)
                    val uiAvg = result.data!!.getDoubleExtra("uiAvg", 0.0)
                    val ramBeforeAvg = result.data!!.getDoubleExtra("ramBeforeAvg", 0.0)
                    val ramAfterAvg = result.data!!.getDoubleExtra("ramAfterAvg", 0.0)
                    val runs = result.data!!.getIntExtra("runs", 0)
                    val logMsg =
                        "Benchmark Status: Done\n" +
                            "CPU avg: ${"%.2f".format(cpuAvg)} ms\n" +
                            "RAM avg: ${"%.2f".format(
                                ramAvg,
                            )} ms, Used: ${"%.2f".format(ramBeforeAvg)} -> ${"%.2f".format(ramAfterAvg)} MB\n" +
                            "UI Latency avg: ${"%.2f".format(uiAvg)} ms\n" +
                            "Runs: $runs"
                    benchmarkStatus.text = logMsg
                    Log.i("BENCHMARK_RESULT", logMsg)
                }
            }

        editRuns.setOnKeyListener { v, keyCode, event ->
            val text = editRuns.text.toString()
            // Usuwanie znaków innych niż cyfry
            val filtered = text.filter { it.isDigit() }
            if (text != filtered) {
                editRuns.setText(filtered)
                editRuns.setSelection(filtered.length)
            }
            false
        }

        findViewById<Button>(R.id.btnStartBenchmark).setOnClickListener {
            val runsText = editRuns.text.toString()
            var runs = runsText.toIntOrNull() ?: 50
            if (runs > 80) runs = 70
            if (runs < 1) runs = 1
            val intent = Intent(this, BenchmarkActivity::class.java)
            intent.putExtra("runs", runs)
            benchmarkLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        reportFullyDrawn()
        val elapsedTime = System.currentTimeMillis() - startTime
        findViewById<TextView>(R.id.appLaunchTime)
            .text = "App Launch Time: ${elapsedTime}ms"
    }
}
