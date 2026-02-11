package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.consts.Config;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ImageLoadingTest extends AppCompatActivity {
    private static final String TAG = "ImageLoadingActivity";

    private RecyclerView recyclerView;

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
            "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20");

    private int loadedImagesCount = 0;
    private long suiteStartTime;
    private String csvPath;
    private BufferedCsvWriter csvWriter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isTestRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setBackgroundColor(Color.WHITE);
        setContentView(recyclerView);

        parseIntentData();

        // Start test after layout is ready
        recyclerView.post(this::startBatchTest);
    }

    private void parseIntentData() {
        csvPath = getIntent().getStringExtra("csv_path");
        Log.i(TAG, "Starting Image Batch: " + Config.samplesAmount);
    }

    private void startBatchTest() {
        if (isTestRunning)
            return;
        isTestRunning = true;

        suiteStartTime = System.currentTimeMillis();
        if (initCsvWriter()) {
            recyclerView.setAdapter(new ImageAdapter());
        } else {
            finish();
        }
    }

    private boolean initCsvWriter() {
        try {
            File file = new File(csvPath != null ? csvPath : getFilesDir() + "/temp_image.csv");
            csvWriter = new BufferedCsvWriter(file, 1000, 64 * 1024);
            csvWriter.initialize();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Writer init failed", e);
            return false;
        }
    }

    private void handleLoadResult(long startTime, boolean success, String details) {
        if (!isTestRunning)
            return;

        long duration = System.currentTimeMillis() - startTime;
        logResult(startTime, duration, success, details);

        loadedImagesCount++;
        if (recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyItemChanged(loadedImagesCount);
        }

        if (loadedImagesCount >= Config.samplesAmount) {
            finishBatch();
            return;
        }

        // Scroll to next item to trigger its binding and loading
        mainHandler.postDelayed(() -> {
            if (isTestRunning) {
                recyclerView.smoothScrollToPosition(loadedImagesCount);
            }
        }, 50);
    }

    private void logResult(long startTime, long duration, boolean success, String details) {
        try {
            TestResult tr = new TestResult("Image Rendering", duration, details, success);
            TestEntry entry = new TestEntry(loadedImagesCount, tr, startTime, duration,
                    System.currentTimeMillis() - suiteStartTime);

            if (csvWriter != null) {
                csvWriter.write(entry);
            }
        } catch (Exception e) {
            Log.e(TAG, "Log failed", e);
        }
    }

    private void finishBatch() {
        if (!isTestRunning)
            return;
        isTestRunning = false;

        closeWriter();
        sendResultAndFinish();
    }

    private void closeWriter() {
        try {
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing writer", e);
        }
    }

    private void sendResultAndFinish() {
        long totalTime = System.currentTimeMillis() - suiteStartTime;
        TestResult result = new TestResult("Image Loading Test", totalTime,
                "Batch completed: " + loadedImagesCount, true);

        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 600));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackgroundColor(Color.LTGRAY);
            imageView.setPadding(0, 0, 0, 4); // Add some margin
            return new ImageViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            String url = IMAGE_URLS.get(position % IMAGE_URLS.size());
            long startTime = System.currentTimeMillis();

            Glide.with(ImageLoadingTest.this)
                    .load(url)
                    .placeholder(new ColorDrawable(Color.LTGRAY))
                    .error(new ColorDrawable(Color.RED))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target,
                                boolean isFirstResource) {
                            if (position == loadedImagesCount) {
                                Log.e(TAG, "Load Error at #" + position + ": "
                                        + (e != null ? e.getMessage() : "Unknown error"));
                                handleLoadResult(startTime, false, "Failed");
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                DataSource dataSource, boolean isFirstResource) {
                            if (position == loadedImagesCount) {
                                handleLoadResult(startTime, true, "Success");
                            }
                            return false;
                        }
                    })
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return Config.samplesAmount;
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            ImageViewHolder(ImageView itemView) {
                super(itemView);
                this.imageView = itemView;
            }
        }
    }
}
