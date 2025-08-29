package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

import java.util.Arrays;
import java.util.List;

public class ImageLoadingActivity extends AppCompatActivity {
    private TextView loadingStatus;
    private RecyclerView recyclerView;
    private long startTime;
    
    private static final List<String> IMAGE_URLS = Arrays.asList(
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
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("ImageLoadingActivity", "onCreate called");
        setContentView(R.layout.activity_image_loading);

        loadingStatus = findViewById(R.id.loadingStatus);
        recyclerView = findViewById(R.id.imageRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        ImageAdapter adapter = new ImageAdapter();
        recyclerView.setAdapter(adapter);
        Log.d("ImageLoadingActivity", "RecyclerView initialized");
        loadingStatus.setText("Loading images...");
        startTime = System.currentTimeMillis();

        // Automatically scroll to the last loaded image at the start
        recyclerView.post(() -> {
            if (adapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);

            }
        });
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageViewHolder> {
        private int loadedImages = 0;

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = (ImageView) getLayoutInflater().inflate(R.layout.item_image, parent, false);
            return new ImageViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            Glide.with(ImageLoadingActivity.this)
                 .load(IMAGE_URLS.get(position))
                 .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                     @Override
                     public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                         return false;
                     }

                     @Override
                     public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                         loadedImages++;
                         Log.d("ImageLoadingActivity", "Image loaded: " + loadedImages + "/" + IMAGE_URLS.size());
                         if (loadedImages == IMAGE_URLS.size()) {
                             long totalTime = System.currentTimeMillis() - startTime;
                             Log.d("ImageLoadingActivity", "All images loaded, transitioning to the next test");
                             TestResult result = new TestResult("Image Loading Test", totalTime, "Image Loading Test Completed", true);
                             Intent intent = new Intent();
                             intent.putExtra(BenchmarkApplication.RESULT, result);
                             setResult(RESULT_OK, intent);
                             finish();
                         }
                         recyclerView.smoothScrollToPosition(loadedImages - 1);
                         return false;
                     }
                 })
                 .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return IMAGE_URLS.size();
        }
    }

    private static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImageViewHolder(ImageView itemView) {
            super(itemView);
            this.imageView = itemView;
        }
    }
}
