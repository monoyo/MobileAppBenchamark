package com.jossy.android.mobilebenchmarkappkotlin

import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var chartMain: LineChart
    private lateinit var chartFactorial: LineChart
    private lateinit var btnStart: Button

    private val results = mutableListOf<BenchmarkResult>()

    data class BenchmarkResult(
        val label: String,
        val timeMillis: Long,
        val inputSize: Int,
        val color: Int,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chartMain = findViewById(R.id.chartMain)
        chartFactorial = findViewById(R.id.chartFactorial)
        btnStart = findViewById(R.id.btnStartBenchmark)

        chartMain.description = Description().apply { text = "Wykres dla O(n), O(n log n)" }
        chartMain.legend.isEnabled = true
        chartMain.setNoDataText("Brak danych do wyświetlenia")

        chartFactorial.description = Description().apply { text = "Wykres dla O(n^2), O(n!)" }
        chartFactorial.legend.isEnabled = true
        chartFactorial.setNoDataText("Brak danych do wyświetlenia")

        btnStart.setOnClickListener {
            performBenchmarks()
        }
    }

    private fun performBenchmarks() {
        results.clear()

        fun benchmarkWithInput(
            label: String,
            inputSizes: List<Int>,
            color: Int,
            block: (Int) -> Unit,
        ) {
            for (size in inputSizes) {
                val wallStart = SystemClock.elapsedRealtime()
                block(size)
                val wallEnd = SystemClock.elapsedRealtime()
                val duration = wallEnd - wallStart

                if (duration >= 0) {
                    results.add(BenchmarkResult(label, duration, size, color))
                    runOnUiThread { drawCharts() }
                }
            }
        }

        Thread {
            benchmarkWithInput("O(n)", listOf(1000, 5000, 10000, 20000, 40000), android.graphics.Color.BLUE) { size ->
                val list = (1..size).toList()
                var sum = 0L
                for (item in list) sum += item
            }

            benchmarkWithInput("O(n log n)", listOf(1000, 5000, 10000, 20000, 40000), android.graphics.Color.GREEN) { size ->
                val list = List(size) { Random.nextInt() }
                list.sorted()
            }

            benchmarkWithInput("O(n^2)", listOf(100, 500, 1000, 2000, 4000), android.graphics.Color.RED) { size ->
                val matrix = Array(size) { IntArray(size) { Random.nextInt() } }
                var total = 0
                for (i in 0 until size) {
                    for (j in 0 until size) {
                        total += matrix[i][j]
                    }
                }
            }

            benchmarkWithInput("O(n!)", listOf(5, 6, 7, 8, 9), android.graphics.Color.MAGENTA) { size ->
                fun generatePermutations(list: List<Int>): List<List<Int>> {
                    if (list.size <= 1) return listOf(list)
                    val perms = mutableListOf<List<Int>>()
                    for (i in list.indices) {
                        val current = list[i]
                        val remaining = list.take(i) + list.drop(i + 1)
                        for (perm in generatePermutations(remaining)) {
                            perms.add(listOf(current) + perm)
                        }
                    }
                    return perms
                }

                generatePermutations((1..size).toList())
            }
        }.start()
    }

    private fun drawCharts() {
        val mainData = LineData()
        val factorialData = LineData()

        val grouped = results.groupBy { it.label }

        grouped.forEach { (label, group) ->
            val entries =
                group
                    .mapNotNull {
                        val x = it.inputSize.toFloat()
                        val y = it.timeMillis.toFloat()
                        if (x >= 0 && y >= 0 && x.isFinite() && y.isFinite()) Entry(x, y) else null
                    }.sortedBy { it.x }

            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, label)
                dataSet.setDrawValues(false)
                dataSet.setDrawCircles(true)
                dataSet.setDrawCircleHole(false)
                dataSet.color = group.first().color
                dataSet.setCircleColor(group.first().color)
                dataSet.lineWidth = 2f

                if (label == "O(n^2)" || label == "O(n!)") {
                    factorialData.addDataSet(dataSet)
                } else {
                    mainData.addDataSet(dataSet)
                }
            }
        }

        chartMain.data = mainData
        chartFactorial.data = factorialData

        fun configureAxis(chart: LineChart) {
            chart.xAxis.apply {
                isEnabled = true
                textSize = 12f
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter =
                    object : ValueFormatter() {
                        override fun getAxisLabel(
                            value: Float,
                            axis: com.github.mikephil.charting.components.AxisBase?,
                        ): String = "n = ${value.toInt()}"
                    }
                textColor = android.graphics.Color.BLACK
                setDrawAxisLine(true)
                setDrawGridLines(true)
                labelRotationAngle = 0f
                granularity = 1f
            }

            chart.axisLeft.apply {
                isEnabled = true
                textSize = 12f
                valueFormatter =
                    object : ValueFormatter() {
                        override fun getAxisLabel(
                            value: Float,
                            axis: com.github.mikephil.charting.components.AxisBase?,
                        ): String = "$value ms"
                    }
                textColor = android.graphics.Color.BLACK
                setDrawAxisLine(true)
                setDrawGridLines(true)
            }

            chart.axisRight.isEnabled = false
            chart.invalidate()
        }

        configureAxis(chartMain)
        configureAxis(chartFactorial)
    }
}
