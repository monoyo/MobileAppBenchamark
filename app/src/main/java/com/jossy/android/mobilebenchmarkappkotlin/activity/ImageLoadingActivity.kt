package com.jossy.android.mobilebenchmarkappkotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.ImageRequest
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult

class ImageLoadingActivity : AppCompatActivity() {
    private lateinit var loadingStatus: TextView
    private lateinit var recyclerView: RecyclerView
    private var startTime: Long = 0

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
            "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ImageLoadingActivity", "onCreate called")
        setContentView(R.layout.activity_image_loading)

        loadingStatus = findViewById(R.id.loadingStatus)
        recyclerView = findViewById(R.id.imageRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = ImageAdapter()
        recyclerView.adapter = adapter
        Log.d("ImageLoadingActivity", "RecyclerView initialized")
        loadingStatus.text = "Loading images..."
        startTime = System.currentTimeMillis()
    }

    private inner class ImageAdapter : RecyclerView.Adapter<ImageViewHolder>() {
        private var processedImages = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val imageView = layoutInflater.inflate(R.layout.item_image, parent, false) as ImageView
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val url = IMAGE_URLS[position]
            holder.imageView.load(url) {
                listener(
                    onSuccess = { _: ImageRequest, _ ->
                        processedImages++
                        Log.d("ImageLoadingActivity", "Image loaded: $processedImages/${IMAGE_URLS.size}")
                        checkIfDone()
                        if (processedImages < IMAGE_URLS.size) {
                            recyclerView.smoothScrollToPosition(processedImages)
                        }
                    },
                    onError = { _: ImageRequest, _ ->
                        processedImages++
                        Log.w("ImageLoadingActivity", "Image failed: $url ($processedImages/${IMAGE_URLS.size})")
                        checkIfDone()
                        recyclerView.smoothScrollToPosition(maxOf(0, processedImages - 1))
                    }
                )
            }
        }

        private fun checkIfDone() {
            if (processedImages == IMAGE_URLS.size) {
                val totalTime = System.currentTimeMillis() - startTime
                Log.d("ImageLoadingActivity", "All images processed, finishing")
                val result = TestResult("Image Loading Test", totalTime, "Image Loading Test Completed", true)
                val intent = Intent()
                intent.putExtra(BenchmarkApplication.RESULT, result)
                setResult(RESULT_OK, intent)
                finish()
            }
        }

        override fun getItemCount(): Int = IMAGE_URLS.size
    }

    private class ImageViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView
    }
}
