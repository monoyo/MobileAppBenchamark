package com.jossy.android.mobilebenchmarkappkotlin

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.os.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var chart: ScatterChart
    private lateinit var btnStart: Button
    private lateinit var tvLiveCpu: TextView
    private lateinit var tvLiveRam: TextView

    private var monitoring = false

    data class BenchmarkResult(val label: String, val cpuUsage: Double, val ramUsage: Double)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart = findViewById(R.id.chart)
        btnStart = findViewById(R.id.btnStartBenchmark)
        tvLiveCpu = findViewById(R.id.tvLiveCpu)
        tvLiveRam = findViewById(R.id.tvLiveRam)

        startLiveMonitoring()

        btnStart.setOnClickListener {
            performBenchmarks { results ->
                drawChart(results)
            }
        }
    }

    private fun startLiveMonitoring() {
        monitoring = true
        Thread {
            while (monitoring) {
                val cpuTime = SystemClock.currentThreadTimeMillis()
                val ram = getUsedMemoryMB()
                runOnUiThread {
                    tvLiveCpu.text = "CPU Time: ${cpuTime}ms"
                    tvLiveRam.text = "RAM: %.1fMB".format(ram)
                }
                Thread.sleep(1000)
            }
        }.start()
    }

    override fun onDestroy() {
        monitoring = false
        super.onDestroy()
    }

    private fun performBenchmarks(onResult: (List<BenchmarkResult>) -> Unit) {
        val results = mutableListOf<BenchmarkResult>()

        fun measure(label: String, block: () -> Unit) {
            val cpuStart = SystemClock.currentThreadTimeMillis()
            val wallStart = SystemClock.elapsedRealtime()
            val ramBefore = getUsedMemoryMB()

            block()

            val cpuEnd = SystemClock.currentThreadTimeMillis()
            val wallEnd = SystemClock.elapsedRealtime()
            val ramAfter = getUsedMemoryMB()

            val cpuTime = cpuEnd - cpuStart
            val wallTime = wallEnd - wallStart
            val cpuUsagePercent = (cpuTime.toDouble() / wallTime) * 100
            val ramUsage = (ramBefore + ramAfter) / 2

            results.add(BenchmarkResult(label, cpuUsagePercent, ramUsage))
        }

        Thread {
            measure("O(1)") { val x = 42 + 58 }
            measure("O(log n)") {
                val list = (1..1_000_000).toList()
                list.binarySearch(999_999)
            }
            measure("O(n)") {
                val list = (1..1_000_000).toList()
                var sum = 0L
                for (item in list) sum += item
            }
            measure("O(n log n)") {
                val list = List(100_000) { Random.nextInt() }
                list.sorted()
            }
            measure("O(n^2)") {
                val size = 300
                val matrix = Array(size) { IntArray(size) { Random.nextInt(0, 100) } }
                var total = 0
                for (i in 0 until size) {
                    for (j in 0 until size) {
                        total += matrix[i][j]
                    }
                }
            }

            results.forEach {
                println("[Benchmark] ${it.label}: CPU = %.2f%%, RAM = %.2fMB".format(it.cpuUsage, it.ramUsage))
            }

            runOnUiThread { onResult(results) }
        }.start()
    }

    private fun drawChart(results: List<BenchmarkResult>) {
        val entries = results.map { Entry(it.cpuUsage.toFloat(), it.ramUsage.toFloat()) }
        val dataSet = ScatterDataSet(entries, "Benchmark")
        dataSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE)
        dataSet.setDrawValues(false)
        dataSet.color = Color.CYAN

        chart.data = ScatterData(dataSet)

        chart.xAxis.apply {
            isEnabled = true
            textSize = 12f
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                    return "%.0f%%".format(value)
                }
            }
            setDrawAxisLine(true)
            setDrawGridLines(true)
        }

        chart.axisLeft.apply {
            isEnabled = true
            textSize = 12f
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                    return "%.1f MB".format(value)
                }
            }
            setDrawAxisLine(true)
            setDrawGridLines(true)
        }

        chart.axisRight.isEnabled = false
        chart.description = Description().apply { text = "" }
        chart.legend.isEnabled = false
        chart.invalidate()
    }

    private fun getUsedMemoryMB(): Double {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
        return info[0].totalPss / 1024.0
    }
}
