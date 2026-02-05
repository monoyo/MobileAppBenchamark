package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.io.BufferedCsvWriter
import com.jossy.android.mobilebenchmarkappkotlin.model.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageLoadingActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var sampleCount: Int = 10000
    private var outputFile: String? = null
    private var csvWriter: BufferedCsvWriter? = null
    
    companion object {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_loading)
        
        statusText = findViewById(R.id.loadingStatus)


        sampleCount = intent.getIntExtra("sample_count", 10000)
        outputFile = intent.getStringExtra("output_file")
        
        initializeWriter()
        startTestLoop()
    }

    private fun initializeWriter() {
        outputFile?.let { path ->
            csvWriter = BufferedCsvWriter(File(path), 1000, 64 * 1024)
            csvWriter?.initialize()
        }
    }

    private fun startTestLoop() {
        lifecycleScope.launch(Dispatchers.IO) { // Network on IO
            val startTime = System.currentTimeMillis()
            var totalDuration = 0L
            val imageLoader = imageLoader

            for (i in 0 until sampleCount) {
                val url = IMAGE_URLS[i % IMAGE_URLS.size]
                val loopStart = System.currentTimeMillis()

                val request = ImageRequest.Builder(this@ImageLoadingActivity)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build()

                try {
                    val result = imageLoader.execute(request)
                    // Wait for result
                } catch (e: Exception) {
                    Log.e("ImageTest", "Failed $i", e)
                }
                
                val loopEnd = System.currentTimeMillis()
                val duration = loopEnd - loopStart
                totalDuration += duration

                csvWriter?.write(
                    iteration = i,
                    executionTimeMs = duration,
                    details = "url=$url",
                    intervalStartMs = loopStart,
                    intervalDurationMs = duration,
                    cumulativeTimeMs = loopEnd - startTime
                )
                
                if(i % 50 == 0) {
                     withContext(Dispatchers.Main) {
                         // Update UI
                     }
                }
            }

            csvWriter?.close()

            withContext(Dispatchers.Main) {
                val result = TestResult(
                    "Image Loading Test",
                    System.currentTimeMillis() - startTime,
                    "Completed $sampleCount loads",
                    true
                )
                val intent = Intent()
                intent.putExtra(BenchmarkApplication.RESULT, result)
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }
}
