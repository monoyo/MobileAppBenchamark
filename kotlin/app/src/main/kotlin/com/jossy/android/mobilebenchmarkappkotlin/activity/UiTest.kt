package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.os.Environment
import android.view.Choreographer
import android.widget.FrameLayout
import android.widget.TextView
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.consts.Config
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult
import com.jossy.android.mobilebenchmarkappkotlin.view.StressTestView
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class UiTest : AppCompatActivity(), Choreographer.FrameCallback {

    companion object {
        private const val TAG = "UiTest"
        private const val INITIAL_OBJECT_COUNT = 250
        private const val OBJECT_INCREMENT_PER_SECOND = 250
        private const val OBJECT_SIZE = 50f
        private const val MIN_FPS = 10.0
        private const val WARMUP_FRAMES = 30
    }

    private lateinit var stressTestView: StressTestView
    private lateinit var infoText: TextView
    private lateinit var container: FrameLayout

    private var isRunning = false
    private var startTime = 0L
    private var lastFrameTimeNanos = 0L
    private var currentObjectCount = INITIAL_OBJECT_COUNT

    private val objects = mutableListOf<RectF>()
    private val colors = mutableListOf<Int>()
    private val velocitiesX = mutableListOf<Float>()
    private val velocitiesY = mutableListOf<Float>()
    private val random = Random

    private var width = 0
    private var height = 0
    private var csvWriter: BufferedWriter? = null
    private var frameCount = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initUI()
        initObjects(INITIAL_OBJECT_COUNT)
        setupCsv()

        container.post {
            width = container.width
            height = container.height
            startTest()
        }
    }

    private fun initUI() {
        container = FrameLayout(this)
        stressTestView = StressTestView(this)
        infoText = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 16f
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.argb(150, 255, 255, 255))
        }

        container.addView(stressTestView)
        container.addView(infoText)
        setContentView(container)
    }

    private fun setupCsv() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = getBenchmarkDirectory(timestamp)

            val file = File(dir, "ui_test_$timestamp.csv")
            csvWriter = BufferedWriter(FileWriter(file))
            csvWriter?.write("Frame,ObjectCount,FrameTimeMs,FPS,ElapsedMs\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create CSV writer", e)
        }
    }

    private fun getBenchmarkDirectory(timestamp: String): File {
        val sessionPath = intent.getStringExtra("session_dir")
        return if (sessionPath != null) {
            File(sessionPath)
        } else {
            File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "benchmarks/$timestamp").apply {
                if (!exists()) mkdirs()
            }
        }
    }

    private fun initObjects(count: Int) {
        objects.clear()
        colors.clear()
        velocitiesX.clear()
        velocitiesY.clear()
        currentObjectCount = 0
        addObjects(count)
    }

    private fun addObjects(count: Int) {
        if (count <= 0) return

        val maxW = if (width > 0) width else 1000
        val maxH = if (height > 0) height else 2000

        repeat(count) {
            val x = random.nextFloat() * (maxW - OBJECT_SIZE)
            val y = random.nextFloat() * (maxH - OBJECT_SIZE)

            objects.add(RectF(x, y, x + OBJECT_SIZE, y + OBJECT_SIZE))
            colors.add(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)))
            velocitiesX.add((random.nextFloat() - 0.5f) * 20)
            velocitiesY.add((random.nextFloat() - 0.5f) * 20)
        }
        currentObjectCount += count
    }

    private fun startTest() {
        isRunning = true
        startTime = System.currentTimeMillis()
        lastFrameTimeNanos = System.nanoTime()
        frameCount = 0

        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        val currentNano = System.nanoTime()
        val frameTimeMs = (currentNano - lastFrameTimeNanos) / 1_000_000.0
        lastFrameTimeNanos = currentNano
        val fps = if (frameTimeMs > 0) 1000.0 / frameTimeMs else 0.0

        val elapsedMs = System.currentTimeMillis() - startTime

        logFrameData(frameTimeMs, fps, elapsedMs)
        manageObjectCount(elapsedMs)
        updatePositions()
        updateUI(fps, elapsedMs)

        frameCount++

        if (frameCount >= Config.sampleCount) {
            finishTest(true)
        } else if (frameCount > WARMUP_FRAMES && fps <= MIN_FPS) {
            Log.i(TAG, "FPS dropped to $fps (<= $MIN_FPS), stopping test at frame $frameCount")
            finishTest(true)
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun logFrameData(frameTimeMs: Double, fps: Double, elapsedMs: Long) {
        try {
            csvWriter?.write(
                String.format(
                    Locale.US,
                    "%d,%d,%.2f,%.2f,%d\n",
                    frameCount, currentObjectCount, frameTimeMs, fps, elapsedMs
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "CSV write failed", e)
        }
    }

    private fun manageObjectCount(elapsedMs: Long) {
        val secondsElapsed = (elapsedMs / 1000).toInt()
        val desiredObjects = INITIAL_OBJECT_COUNT + (secondsElapsed * OBJECT_INCREMENT_PER_SECOND)

        if (desiredObjects > currentObjectCount) {
            addObjects(desiredObjects - currentObjectCount)
        }
    }

    private fun updatePositions() {
        if (width == 0) return

        objects.forEachIndexed { i, rect ->
            val vx = velocitiesX[i]
            val vy = velocitiesY[i]

            rect.offset(vx, vy)
            handleBoundsCollision(i, rect, vx, vy)
        }
    }

    private fun handleBoundsCollision(index: Int, rect: RectF, vx: Float, vy: Float) {
        if (rect.left < 0 || rect.right > width) {
            velocitiesX[index] = -vx
            rect.offset(-vx * 2, 0f)
        }
        if (rect.top < 0 || rect.bottom > height) {
            velocitiesY[index] = -vy
            rect.offset(0f, -vy * 2)
        }
    }

    private fun updateUI(fps: Double, elapsedMs: Long) {
        stressTestView.setObjects(objects, colors)
        infoText.text = String.format(
            Locale.US,
            "Samples: %d / %d\nObjects: %d\nFPS: %.1f",
            frameCount, Config.sampleCount, currentObjectCount, fps
        )
    }

    private fun finishTest(success: Boolean) {
        isRunning = false
        val duration = System.currentTimeMillis() - startTime

        closeCsvWriter()

        val result = TestResult(
            "UI Stress Test",
            duration,
            String.format(Locale.US, "Max Objects: %d, Samples: %d", currentObjectCount, frameCount),
            success
        )

        Intent().apply {
            putExtra(BenchmarkApplication.RESULT, result)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    private fun closeCsvWriter() {
        try {
            csvWriter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close CSV writer", e)
        }
    }
}
