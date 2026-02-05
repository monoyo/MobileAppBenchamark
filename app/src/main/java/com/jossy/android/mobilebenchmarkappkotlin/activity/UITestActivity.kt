package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.io.BufferedCsvWriter
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import java.io.File
import kotlin.random.Random

class UITestActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private var sampleCount: Int = 10000
    private var outputFile: String? = null
    
    // Benchmark State
    private var frameCount = 0
    private val objects = ArrayList<BouncingRect>()
    private var startTimeMs = 0L
    private var lastFrameTimeNs = 0L
    private var isRunning = false
    private var csvWriter: BufferedCsvWriter? = null
    
    // Config
    private val random = Random.Default
    private val paint = Paint().apply { isAntiAlias = false }
    private var benchmarkView: BenchmarkView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)
        container = findViewById(R.id.container)

        // Read configuration
        sampleCount = intent.getIntExtra("sample_count", 10000)
        outputFile = intent.getStringExtra("output_file")
        
        initializeWriter()

        // Setup View
        benchmarkView = BenchmarkView(this)
        container.addView(benchmarkView)

        startTimeMs = System.currentTimeMillis()
        isRunning = true
        
        // Start load
        addObjects(250)

        // Start Loop
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun initializeWriter() {
        outputFile?.let { path ->
            val file = File(path)
            // Use 64KB buffer
            csvWriter = BufferedCsvWriter(file, 1000, 64 * 1024)
            csvWriter?.initialize()
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameTimeNs == 0L) {
                lastFrameTimeNs = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
                return
            }

            val frameDurationNs = frameTimeNanos - lastFrameTimeNs
            val frameDurationMs = frameDurationNs / 1_000_000.0
            lastFrameTimeNs = frameTimeNanos

            updateLogic()
            benchmarkView?.invalidate()
            
            // Scaling: Add 250 objects every 1 second
            val elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000
            val targetCount = 250 + (elapsedSeconds.toInt() * 250)
            if (objects.size < targetCount) {
                addObjects(targetCount - objects.size)
            }

            // Log Data
            frameCount++
            logFrame(frameCount, frameDurationMs, objects.size)

            if (frameCount >= sampleCount) {
                finishBenchmark()
            } else {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private fun updateLogic() {
        val width = container.width.toFloat()
        val height = container.height.toFloat()
        if (width <= 0 || height <= 0) return
        
        objects.forEach { obj ->
            obj.x += obj.vx
            obj.y += obj.vy
            
            // Bounce
            if (obj.x < 0 || obj.x + obj.size > width) {
                obj.vx = -obj.vx
                obj.x = obj.x.coerceIn(0f, width - obj.size)
            }
            if (obj.y < 0 || obj.y + obj.size > height) {
                obj.vy = -obj.vy
                obj.y = obj.y.coerceIn(0f, height - obj.size)
            }
        }
    }

    private fun addObjects(count: Int) {
        val width = container.width.takeIf { it > 0 } ?: 1080
        val height = container.height.takeIf { it > 0 } ?: 1920
        
        repeat(count) {
            objects.add(
                BouncingRect(
                    x = random.nextFloat() * (width - 50),
                    y = random.nextFloat() * (height - 50),
                    vx = (random.nextFloat() - 0.5f) * 20,
                    vy = (random.nextFloat() - 0.5f) * 20,
                    size = 50f,
                    color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                )
            )
        }
    }

    private fun logFrame(frame: Int, durationMs: Double, objCount: Int) {
        val fps = if (durationMs > 0) 1000.0 / durationMs else 0.0
        val elapsedMs = System.currentTimeMillis() - startTimeMs
        
        csvWriter?.write(
            iteration = frame,
            executionTimeMs = durationMs.toLong(),
            details = String.format(java.util.Locale.US, "%.2f,%.2f,%d", durationMs, fps, elapsedMs),
            intervalStartMs = System.currentTimeMillis(),
            intervalDurationMs = durationMs.toLong(),
            cumulativeTimeMs = elapsedMs
        )
    }

    private fun finishBenchmark() {
        isRunning = false
        val totalTime = System.currentTimeMillis() - startTimeMs
        
        csvWriter?.close()
        
        val result = TestResult(
            testName = "UI Stress Test",
            executionTime = totalTime,
            details = "Max Objects: ${objects.size}, Samples: $frameCount",
            isSuccessful = true
        )
        
        val intent = Intent()
        intent.putExtra(BenchmarkApplication.RESULT, result)
        setResult(RESULT_OK, intent)
        finish()
    }

    private inner class BenchmarkView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            objects.forEach { obj ->
                paint.color = obj.color
                canvas.drawRect(obj.x, obj.y, obj.x + obj.size, obj.y + obj.size, paint)
            }
        }
    }

    data class BouncingRect(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val size: Float,
        val color: Int
    )
}
