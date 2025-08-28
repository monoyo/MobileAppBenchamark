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
import com.bumptech.glide.Glide
import com.jossy.android.mobilebenchmarkappkotlin.BenchmarkApplication
import com.jossy.android.mobilebenchmarkappkotlin.R
import com.jossy.android.mobilebenchmarkappkotlin.data.TestResult

class ImageLoadingActivity : AppCompatActivity() {
    private lateinit var loadingStatus: TextView
    private lateinit var recyclerView: RecyclerView
    private var startTime: Long = 0

    companion object {
        private val IMAGE_URLS = listOf(
            "https://picsum.photos/300/200?random=1",
            "https://picsum.photos/300/200?random=2",
            "https://picsum.photos/300/200?random=3",
            "https://picsum.photos/300/200?random=4",
            "https://picsum.photos/300/200?random=5",
            "https://picsum.photos/300/200?random=6",
            "https://picsum.photos/300/200?random=7",
            "https://picsum.photos/300/200?random=8",
            "https://picsum.photos/300/200?random=9",
            "https://picsum.photos/300/200?random=10"
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

        recyclerView.post {
            if (adapter.itemCount > 0) recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        })
    }

    private inner class ImageAdapter : RecyclerView.Adapter<ImageViewHolder>() {
        private var loadedImages = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val imageView = layoutInflater.inflate(R.layout.item_image, parent, false) as ImageView
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            Glide.with(this@ImageLoadingActivity)
                .load(IMAGE_URLS[position])
                .into(object : com.bumptech.glide.request.target.DrawableImageViewTarget(holder.imageView) {
                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        loadedImages++
                        Log.w("ImageLoadingActivity", "Image failed to load: ${IMAGE_URLS[position]} (processed $loadedImages/${IMAGE_URLS.size})")
                        if (loadedImages == IMAGE_URLS.size) {
                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d("ImageLoadingActivity", "All images processed (loaded or failed), transitioning to the next test")
                            val result = TestResult("Image Loading Test", totalTime, "Image Loading Test Completed", true)
                            val intent = Intent()
                            intent.putExtra(BenchmarkApplication.RESULT, result)
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                        recyclerView.smoothScrollToPosition(maxOf(0, loadedImages - 1))
                    }

                    override fun onResourceReady(resource: android.graphics.drawable.Drawable, transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?) {
                        super.onResourceReady(resource, transition)
                        holder.imageView.setImageDrawable(resource)
                        loadedImages++
                        Log.d("ImageLoadingActivity", "Image loaded: $loadedImages/${IMAGE_URLS.size}")
                        if (loadedImages == IMAGE_URLS.size) {
                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d("ImageLoadingActivity", "All images loaded, transitioning to the next test")
                            val result = TestResult("Image Loading Test", totalTime, "Image Loading Test Completed", true)
                            val intent = Intent()
                            intent.putExtra(BenchmarkApplication.RESULT, result)
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                        recyclerView.smoothScrollToPosition(loadedImages - 1)
                    }
                })
        }

        override fun getItemCount(): Int = IMAGE_URLS.size
    }

    private class ImageViewHolder(itemView: ImageView) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView
    }
}
