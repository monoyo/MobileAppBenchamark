package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

import java.util.Arrays;
import java.util.List;

public class ImageLoadingActivity extends AppCompatActivity {
    private TextView loadingStatus;

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
    private int targetSamples = 0;
    private long suiteStartTime;
    private String csvPath;
    private com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter csvWriter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_loading);

        loadingStatus = findViewById(R.id.loadingStatus);

        targetSamples = getIntent().getIntExtra("iterations", 10);
        csvPath = getIntent().getStringExtra("csv_path");

        Log.i("ImageLoadingActivity", "Starting Image Batch: " + targetSamples);
        loadingStatus.setText("Initializing Batch...");

        startBatchTest();
    }

    private void startBatchTest() {
        suiteStartTime = System.currentTimeMillis();

        try {
            csvWriter = new com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter(
                    new java.io.File(csvPath != null ? csvPath : getFilesDir() + "/temp_image.csv"), 1000, 64 * 1024);
            csvWriter.initialize();
        } catch (Exception e) {
            Log.e("ImageLoadingActivity", "Writer init failed", e);
            finish();
            return;
        }

        loadNextImage();
    }

    private void loadNextImage() {
        if (loadedImagesCount >= targetSamples) {
            finishBatch();
            return;
        }

        String url = IMAGE_URLS.get(loadedImagesCount % IMAGE_URLS.size());
        long start = System.currentTimeMillis();

        Glide.with(this)
                .load(url)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model,
                                               @NonNull Target<Drawable> target, boolean isFirstResource) {
                        recordResult(start, false, "Failed");
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                 @NonNull Target<Drawable> target, @NonNull DataSource dataSource,
                                                 boolean isFirstResource) {
                        recordResult(start, true, "Success");
                        return false;
                    }
                })
                .preload();
    }

    private void recordResult(long startTime, boolean success, String details) {
        long duration = System.currentTimeMillis() - startTime;

        try {
            com.jossy.android.mobilebenchmarkappjava.data.TestResult tr = new com.jossy.android.mobilebenchmarkappjava.data.TestResult(
                    "Image Loading", duration, details, success);
            com.jossy.android.mobilebenchmarkappjava.data.TestEntry entry = new com.jossy.android.mobilebenchmarkappjava.data.TestEntry(
                    loadedImagesCount, tr, startTime, duration, System.currentTimeMillis() - suiteStartTime);

            if (csvWriter != null)
                csvWriter.write(entry);

        } catch (Exception e) {
            Log.e("ImageLoadingActivity", "Log failed", e);
        }

        loadedImagesCount++;

        // Update UI occasionally
        if (loadedImagesCount % 50 == 0) {
            runOnUiThread(() -> loadingStatus.setText("Image Test: " + loadedImagesCount + " / " + targetSamples));
        }

        // Break the recursion by posting to the handler
        mainHandler.post(this::loadNextImage);
    }

    private void finishBatch() {
        long totalTime = System.currentTimeMillis() - suiteStartTime;
        try {
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
            }
        } catch (Exception e) {
            Log.e("ImageLoadingActivity", "Error closing writer", e);
        }

        TestResult result = new TestResult("Image Loading Test", totalTime, "Batch completed: " + loadedImagesCount,
                true);
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}
