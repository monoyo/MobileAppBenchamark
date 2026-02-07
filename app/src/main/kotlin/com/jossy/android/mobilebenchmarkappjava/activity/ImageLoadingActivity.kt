package com.jossy.android.mobilebenchmarkappjava.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
    private lateinit var benchmarkImageView: ImageView
    private var loadedImagesCount = 0
    private var suiteStartTime = 0L
    private var csvPath: String? = null
    private var csvWriter: BufferedCsvWriter? = null
    private val progressLiveData = MutableLiveData<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initUI()
        csvPath = intent.getStringExtra("csv_path")
        benchmarkImageView.post { startBatchTest() }
    }

    private fun initUI() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        
        loadingStatus = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        
        benchmarkImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(900, 600)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.LTGRAY)
        }

        rootLayout.addView(loadingStatus)
        rootLayout.addView(benchmarkImageView)
        setContentView(rootLayout)

        progressLiveData.observe(this) { count ->
            loadingStatus.text = "Rendering Image #$count / ${Config.samplesAmount}"
        }
    }

    private fun startBatchTest() {
        suiteStartTime = System.currentTimeMillis()
        val file = File(csvPath ?: "${filesDir}/image_benchmark.csv")
        csvWriter = BufferedCsvWriter(file, 1000, 64 * 1024).apply { initialize() }
        loadNextImage()
    }

    private fun loadNextImage() {
        if (loadedImagesCount >= Config.samplesAmount || isFinishing) {
            finishBatch()
            return
        }
        
        val url = IMAGE_URLS[loadedImagesCount % IMAGE_URLS.size]
        val startTime = System.currentTimeMillis()

        Glide.with(this)
            .load(url)
            .placeholder(ColorDrawable(Color.LTGRAY)) // Widzisz szary? Czeka na sieć.
            .error(ColorDrawable(Color.RED))         // Widzisz czerwony? Błąd (np. blokada IP).
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, m: Any?, t: Target<Drawable>, f: Boolean): Boolean {
                    Log.e(TAG, "Load Error at #$loadedImagesCount: ${e?.message}")
                    handleLoadResult(startTime, false, "Error: ${e?.message}")
                    return false
                }

                override fun onResourceReady(r: Drawable, m: Any, t: Target<Drawable>, d: DataSource, f: Boolean): Boolean {
                    handleLoadResult(startTime, true, "Success")
                    return false
                }
            })
            .into(benchmarkImageView)
    }

    private fun handleLoadResult(startTime: Long, success: Boolean, details: String) {
        val duration = System.currentTimeMillis() - startTime
        
        try {
            val tr = TestResult("Image Rendering", duration, details, success)
            csvWriter?.write(TestEntry(loadedImagesCount, tr, startTime, duration, System.currentTimeMillis() - suiteStartTime))
        } catch (e: Exception) {}

        loadedImagesCount++
        progressLiveData.postValue(loadedImagesCount)
        
        // KLUCZOWE: Dodajemy małe opóźnienie, aby uniknąć "zasypania" UI wątku 
        // i pozwolić na fizyczne odświeżenie ekranu (min. 16ms dla 60fps).
        benchmarkImageView.postDelayed({
            loadNextImage()
        }, 16) 
    }

    private fun finishBatch() {
        val totalTime = System.currentTimeMillis() - suiteStartTime
        Log.i(TAG, "Benchmark Finished. Total: $loadedImagesCount, Time: ${totalTime}ms")
        try { csvWriter?.apply { flush(); close() } } catch (e: Exception) {}
        
        val result = TestResult("Image Rendering Test", totalTime, "Rendered $loadedImagesCount images", true)
        setResult(RESULT_OK, Intent().apply { putExtra(BenchmarkApplication.RESULT, result) })
        finish()
    }
}
