package com.jossy.android.mobilebenchmarkappkotlin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BenchmarkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this)
        setContentView(textView)
        textView.text = "Benchmark Status: Running..."

        val cpuTimes = mutableListOf<Long>()
        val ramTimes = mutableListOf<Long>()
        val ramUsages = mutableListOf<Pair<Long, Long>>()
        val uiLatencies = mutableListOf<Long>()
        val cpuResults = mutableListOf<Int>()
        val runs = 50

        fun runSingleBenchmark(
            runIdx: Int,
            onFinish: () -> Unit,
        ) {
            val cpuStart = System.nanoTime()
            val cpuResult = countPrimes(100_000)
            val cpuTime = (System.nanoTime() - cpuStart) / 1_000_000
            cpuTimes.add(cpuTime)
            cpuResults.add(cpuResult)

            val ramStart = System.nanoTime()
            val ramUsageBefore = getUsedMemoryMB()
            val bigArray = IntArray(500_000) { it } // zmniejszony rozmiar tablicy
            val ramUsageAfter = getUsedMemoryMB()
            val ramTime = (System.nanoTime() - ramStart) / 1_000_000
            ramTimes.add(ramTime)
            ramUsages.add(ramUsageBefore to ramUsageAfter)
            bigArray.fill(0)
            System.gc()

            val uiStart = System.nanoTime()
            runOnUiThread {
                val uiEnd = System.nanoTime()
                val uiLatency = (uiEnd - uiStart) / 1_000_000
                uiLatencies.add(uiLatency)
                onFinish()
            }
        }

        fun runBenchmarks(current: Int = 0) {
            if (current >= runs) {
                val cpuAvg = cpuTimes.average()
                val ramAvg = ramTimes.average()
                val uiAvg = uiLatencies.average()
                val ramBeforeAvg = ramUsages.map { it.first }.average()
                val ramAfterAvg = ramUsages.map { it.second }.average()
                val resultIntent =
                    Intent().apply {
                        putExtra("cpuAvg", cpuAvg)
                        putExtra("ramAvg", ramAvg)
                        putExtra("uiAvg", uiAvg)
                        putExtra("ramBeforeAvg", ramBeforeAvg)
                        putExtra("ramAfterAvg", ramAfterAvg)
                        putExtra("runs", runs)
                    }
                setResult(Activity.RESULT_OK, resultIntent)
                textView.text = "Benchmark Status: Done\n" +
                    "CPU avg: ${"%.2f".format(cpuAvg)} ms\n" +
                    "RAM avg: ${"%.2f".format(ramAvg)} ms, Used: ${"%.2f".format(ramBeforeAvg)} -> ${"%.2f".format(ramAfterAvg)} MB\n" +
                    "UI Latency avg: ${"%.2f".format(uiAvg)} ms\n" +
                    "Runs: $runs\n\nZamknij okno, aby wrócić."
                // Zamknij aktywność po krótkim czasie
                textView.postDelayed({ finish() }, 2000)
                return
            }
            runSingleBenchmark(current) {
                runBenchmarks(current + 1)
            }
        }

        runBenchmarks()
    }

    private fun countPrimes(limit: Int): Int {
        var count = 0
        for (i in 2..limit) {
            if (isPrime(i)) count++
        }
        return count
    }

    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        for (i in 2..Math.sqrt(n.toDouble()).toInt()) {
            if (n % i == 0) return false
        }
        return true
    }

    private fun getUsedMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
}
