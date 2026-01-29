package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import java.util.Random

class UITestActivity : AppCompatActivity() {
    private lateinit var container: FrameLayout
    private var startTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)
        container = findViewById(R.id.container)

        Log.d("UITestActivity", "Starting UI test")
        startTime = System.currentTimeMillis()
        startUITest()
    }

    private fun startUITest() {
        val size = 50
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val random = Random()

        for (i in 0 until 1500) {
            val view = View(this)
            view.setBackgroundColor(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)))
            val layoutParams = FrameLayout.LayoutParams(size, size)
            view.layoutParams = layoutParams
            view.x = random.nextInt(screenWidth - size).toFloat()
            view.y = random.nextInt(screenHeight - size).toFloat()

            container.addView(view)

            val animX = ObjectAnimator.ofFloat(view, "translationX", view.x, view.x + random.nextInt(400) - 200f, view.x)
            val animY = ObjectAnimator.ofFloat(view, "translationY", view.y, view.y + random.nextInt(400) - 200f, view.y)

            animX.repeatCount = ObjectAnimator.INFINITE
            animY.repeatCount = ObjectAnimator.INFINITE
            animX.duration = 1000L
            animY.duration = 1000L

            animX.start()
            animY.start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("UITestActivity", "UI test completed")
            val executionTime = System.currentTimeMillis() - startTime
            val result = TestResult("UI Test", executionTime, "Animation frames rendered", true)
            val intent = Intent()
            intent.putExtra(BenchmarkApplication.RESULT, result)
            setResult(RESULT_OK, intent)
            finish()
        }, 2000)
    }
}
