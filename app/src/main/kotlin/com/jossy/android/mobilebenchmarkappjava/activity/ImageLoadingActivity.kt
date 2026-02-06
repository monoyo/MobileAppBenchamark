package com.jossy.android.mobilebenchmarkappjava.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
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

class ImageLoadingActivity : AppCompatActivity() {
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

    private lateinit var loadingStatus: TextView
    private var loadedImagesCount = 0
    private var suiteStartTime = 0L
    private var csvPath: String? = null
    private var csvWriter: BufferedCsvWriter? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val progressLiveData = MutableLiveData<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
        parseIntentData()
        startBatchTest()
    }

    private fun initUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }
        
        loadingStatus = TextView(this).apply {
            text = "Initializing Image Batch..."
            textSize = 16f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(loadingStatus)
        setContentView(layout)

        progressLiveData.observe(this) { currentIteration ->
            loadingStatus.text = "Image Loading Progress: $currentIteration / ${Config.samplesAmount}"
        }
    }

    private fun parseIntentData() {
        csvPath = intent.getStringExtra("csv_path")
        Log.i(TAG, "Starting Image Batch: ${Config.samplesAmount}")
    }

    private fun startBatchTest() {
        suiteStartTime = System.currentTimeMillis()
        if (initCsvWriter()) {
            loadNextImage()
        } else {
            finish()
        }
    }

    private fun initCsvWriter(): Boolean = try {
        val file = File(csvPath ?: "${filesDir}/temp_image.csv")
        csvWriter = BufferedCsvWriter(file, 1000, 64 * 1024).apply { initialize() }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Writer init failed", e)
        false
    }

    private fun loadNextImage() {
        if (loadedImagesCount >= Config.samplesAmount) {
            finishBatch()
            return
        }
        performImageLoad()
    }

    private fun performImageLoad() {
        val url = IMAGE_URLS[loadedImagesCount % IMAGE_URLS.size]
        val startTime = System.currentTimeMillis()

        Glide.with(this)
            .load(url)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(createRequestListener(startTime))
            .preload()
    }

    private fun createRequestListener(startTime: Long) = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ) = handleLoadResult(startTime, false, "Failed").let { false }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ) = handleLoadResult(startTime, true, "Success").let { false }
    }

    private fun handleLoadResult(startTime: Long, success: Boolean, details: String) {
        val duration = System.currentTimeMillis() - startTime
        logResult(startTime, duration, success, details)
        loadedImagesCount++
        progressLiveData.postValue(loadedImagesCount)
        scheduleNextLoad()
    }

    private fun logResult(startTime: Long, duration: Long, success: Boolean, details: String) {
        try {
            val tr = TestResult("Image Loading", duration, details, success)
            val entry = TestEntry(
                loadedImagesCount, tr, startTime, duration,
                System.currentTimeMillis() - suiteStartTime
            )
            csvWriter?.write(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Log failed", e)
        }
    }

    private fun scheduleNextLoad() {
        mainHandler.post(::loadNextImage)
    }

    private fun finishBatch() {
        closeWriter()
        sendResultAndFinish()
    }

    private fun closeWriter() {
        try {
            csvWriter?.apply {
                flush()
                close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing writer", e)
        }
    }

    private fun sendResultAndFinish() {
        val totalTime = System.currentTimeMillis() - suiteStartTime
        val result = TestResult("Image Loading Test", totalTime, "Batch completed: $loadedImagesCount", true)

        Intent().apply {
            putExtra(BenchmarkApplication.RESULT, result)
            setResult(RESULT_OK, this)
        }
        finish()
    }
}
