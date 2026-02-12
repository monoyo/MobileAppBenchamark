package com.jossy.android.mobilebenchmarkappjava.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappjava.consts.Config
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry
import com.jossy.android.mobilebenchmarkappjava.data.TestResult
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter
import java.io.File

class ImageLoadingTest : AppCompatActivity() {
    companion object {
        private const val TAG = "ImageLoadingActivity"
        private val IMAGE_URLS = listOf(
            "https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4",
            "https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM",
            "https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY",
            "https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c",
            "https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY",
            "https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo",
            "https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA",
            "https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc",
            "https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps",
            "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20"
        )
    }

    private lateinit var recyclerView: RecyclerView
    private var loadedImagesCount = 0
    private var suiteStartTime = 0L
    private var csvPath: String? = null
    private var csvWriter: BufferedCsvWriter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTestRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        recyclerView = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(this@ImageLoadingTest)
            setBackgroundColor(Color.WHITE)
        }
        setContentView(recyclerView)
        
        parseIntentData()
        
        // Start test after layout is ready
        recyclerView.post { startBatchTest() }
    }

    private fun parseIntentData() {
        csvPath = intent.getStringExtra("csv_path")
        Log.i(TAG, "Starting Image Batch: ${Config.sampleCount}")
    }

    private fun startBatchTest() {
        if (isTestRunning) return
        isTestRunning = true
        
        suiteStartTime = System.currentTimeMillis()
        if (initCsvWriter()) {
            recyclerView.adapter = ImageAdapter()
        } else {
            finish()
        }
    }

    private fun initCsvWriter(): Boolean {
        return try {
            val file = File(csvPath ?: "\${filesDir}/temp_image.csv")
            csvWriter = BufferedCsvWriter(file, 1000, 64 * 1024)
            csvWriter?.initialize()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Writer init failed", e)
            false
        }
    }

    private fun handleLoadResult(startTime: Long, success: Boolean, details: String) {
        if (!isTestRunning) return

        val duration = System.currentTimeMillis() - startTime
        logResult(startTime, duration, success, details)

        loadedImagesCount++
        recyclerView.adapter?.notifyItemChanged(loadedImagesCount)

        if (loadedImagesCount >= Config.sampleCount) {
            finishBatch()
            return
        }

        // Scroll to next item to trigger its binding and loading
        handler.postDelayed({
            if (isTestRunning) {
                recyclerView.smoothScrollToPosition(loadedImagesCount)
            }
        }, 50)
    }

    private fun logResult(startTime: Long, duration: Long, success: Boolean, details: String) {
        try {
            val tr = TestResult("Image Rendering", duration, details, success)
            csvWriter?.write(
                TestEntry(loadedImagesCount, tr, startTime, duration, 
                System.currentTimeMillis() - suiteStartTime)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Log failed", e)
        }
    }

    private fun finishBatch() {
        if (!isTestRunning) return
        isTestRunning = false
        
        closeWriter()
        sendResultAndFinish()
    }

    private fun closeWriter() {
        try {
            csvWriter?.flush()
            csvWriter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing writer", e)
        }
    }

    private fun sendResultAndFinish() {
        val totalTime = System.currentTimeMillis() - suiteStartTime
        val result = TestResult("Image Loading Test", totalTime,
                "Batch completed: $loadedImagesCount", true)

        val intent = Intent()
        intent.putExtra(BenchmarkApplication.RESULT, result)
        setResult(RESULT_OK, intent)
        finish()
    }

    inner class ImageAdapter : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 600
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.LTGRAY)
                setPadding(0, 0, 0, 4) // Add some margin
            }
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val url = IMAGE_URLS[position % IMAGE_URLS.size]
            val startTime = System.currentTimeMillis()

            Glide.with(this@ImageLoadingTest)
                .load(url)
                .placeholder(ColorDrawable(Color.LTGRAY))
                .error(ColorDrawable(Color.RED))
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        if (position == loadedImagesCount) {
                            Log.e(TAG, "Load Error at #$position: \${e?.message}")
                            handleLoadResult(startTime, false, "Failed")
                        }
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        if (position == loadedImagesCount) {
                            handleLoadResult(startTime, true, "Success")
                        }
                        return false
                    }
                })
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = Config.sampleCount

        inner class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
    }
}
