package com.jossy.android.mobilebenchmarkappkotlin

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

class BenchmarkActivity : AppCompatActivity() {
    private lateinit var container: FrameLayout
    private var frameCount = 0
    private var startTime = 0L
    private var lastFps: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startFPSCounter()
        setContentView(R.layout.activity_benchmark)
        container = findViewById(R.id.container)
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { startUITest() }
            delay(10000)
            withContext(Dispatchers.Main) {
                container.removeAllViews()
                container.setBackgroundColor(Color.WHITE)
                container.addView(
                    TextView(this@BenchmarkActivity).apply {
                        text = "Test UI ended. AVG FPS: $lastFps"
                        setTextColor(Color.BLACK)
                        textSize = 20f
                        layoutParams =
                            FrameLayout
                                .LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    setMargins(16, 16, 16, 16)
                                }
                    },
                )
            }
            val cpuStart = System.currentTimeMillis()
            repeat(7) {
                launch(Dispatchers.IO) {
                    CPUTest.runBenchmark()
                }
            }
            CPUTest.runBenchmark()
            val cpuElapsed = System.currentTimeMillis() - cpuStart
            Log.i("BenchmarkActivity", "CPU test time: ${cpuElapsed}ms")
            withContext(Dispatchers.Main) {
                container.addView(
                    TextView(this@BenchmarkActivity).apply {
                        text = "Test CPU ended. Time: $cpuElapsed ms"
                        setTextColor(Color.BLACK)
                        textSize = 20f
                        layoutParams =
                            FrameLayout
                                .LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    setMargins(16, 16, 16, 16)
                                    setPadding(0, 80, 0, 0)
                                }
                    },
                )
            }
            val ramStart = System.currentTimeMillis()
            RAMTest.runBenchmark()
            val ramElapsed = System.currentTimeMillis() - ramStart
            Log.i("BenchmarkActivity", "RAM test time: ${ramElapsed}ms")
            withContext(Dispatchers.Main) {
                container.addView(
                    TextView(this@BenchmarkActivity).apply {
                        text = "Test RAM ended. Time: $ramElapsed ms"
                        setTextColor(Color.BLACK)
                        textSize = 20f
                        layoutParams =
                            FrameLayout
                                .LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                ).apply {
                                    setMargins(16, 16, 16, 16)
                                    setPadding(0, 160, 0, 0)
                                }
                    },
                )
            }
        }
    }

    private fun startUITest() {
        val size = 50
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        repeat(1500) {
            val view =
                View(this).apply {
                    setBackgroundColor(Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)))
                    layoutParams = FrameLayout.LayoutParams(size, size)
                    x = Random.nextInt(screenWidth - size).toFloat()
                    y = Random.nextInt(screenHeight - size).toFloat()
                }
            container.addView(view)

            val animX = ObjectAnimator.ofFloat(view, "translationX", view.x, view.x + Random.nextInt(-200, 200), view.x)
            val animY = ObjectAnimator.ofFloat(view, "translationY", view.y, view.y + Random.nextInt(-200, 200), view.y)

            animX.repeatCount = ObjectAnimator.INFINITE
            animY.repeatCount = ObjectAnimator.INFINITE
            animX.duration = 2000L
            animY.duration = 2000L

            animX.start()
            animY.start()
        }
    }

    private fun startFPSCounter() {
        startTime = System.nanoTime()
        frameCount = 0

        val frameCallback =
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    frameCount++
                    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
                    if (elapsedSeconds >= 5.0) {
                        val fps = (frameCount / elapsedSeconds).roundToInt()
                        lastFps = fps
                        Log.d("BenchmarkActivity", "🔧 AVG FPS: $fps")
                    } else {
                        Choreographer.getInstance().postFrameCallback(this)
                    }
                }
            }

        Choreographer.getInstance().postFrameCallback(frameCallback)
    }
}
