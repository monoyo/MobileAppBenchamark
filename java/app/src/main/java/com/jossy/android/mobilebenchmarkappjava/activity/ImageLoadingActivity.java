package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;

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

public class ImageLoadingActivity extends AppCompatActivity {
    private static final String TAG = "ImageLoadingActivity";

    private TextView loadingStatus;
    private ImageView benchmarkImageView;

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
    private final MutableLiveData<Integer> progressLiveData = new MutableLiveData<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initUI();
        parseIntentData();
        mainHandler.post(this::startBatchTest);
    }

    private void initUI() {
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setBackgroundColor(Color.WHITE);
        rootLayout.setGravity(Gravity.CENTER);

        loadingStatus = new TextView(this);
        loadingStatus.setTextSize(18f);
        loadingStatus.setTextColor(Color.BLACK);
        loadingStatus.setGravity(Gravity.CENTER);
        loadingStatus.setPadding(0, 20, 0, 20);
        loadingStatus.setText(R.string.initializing_image_batch);

        benchmarkImageView = new ImageView(this);
        benchmarkImageView.setLayoutParams(new LinearLayout.LayoutParams(900, 600));
        benchmarkImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        benchmarkImageView.setBackgroundColor(Color.LTGRAY);

        rootLayout.addView(loadingStatus);
        rootLayout.addView(benchmarkImageView);
        setContentView(rootLayout);

        progressLiveData.observe(this, currentIteration -> loadingStatus
                .setText(getString(R.string.image_test_progress, currentIteration, Config.samplesAmount)));
    }

    private void parseIntentData() {
        csvPath = getIntent().getStringExtra("csv_path");
        Log.i(TAG, "Starting Image Batch: " + Config.samplesAmount);
    }

    private void startBatchTest() {
        suiteStartTime = System.currentTimeMillis();
        if (initCsvWriter()) {
            loadNextImage();
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

    private void loadNextImage() {
        if (loadedImagesCount >= Config.samplesAmount) {
            finishBatch();
            return;
        }
        performImageLoad();
    }

    private void performImageLoad() {
        String url = IMAGE_URLS.get(loadedImagesCount % IMAGE_URLS.size());
        long startTime = System.currentTimeMillis();

        Glide.with(this)
                .load(url)
                .placeholder(new ColorDrawable(Color.LTGRAY))
                .error(new ColorDrawable(Color.RED))
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .listener(createRequestListener(startTime))
                .into(benchmarkImageView);
    }

    private RequestListener<Drawable> createRequestListener(long startTime) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model,
                    @NonNull Target<Drawable> target, boolean isFirstResource) {
                Log.e(TAG,
                        "Load Error at #" + loadedImagesCount + ": " + (e != null ? e.getMessage() : "Unknown error"));
                handleLoadResult(startTime, false, "Failed");
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                    @NonNull Target<Drawable> target, @NonNull DataSource dataSource,
                    boolean isFirstResource) {
                handleLoadResult(startTime, true, "Success");
                return false;
            }
        };
    }

    private void handleLoadResult(long startTime, boolean success, String details) {
        long duration = System.currentTimeMillis() - startTime;
        logResult(startTime, duration, success, details);

        loadedImagesCount++;
        progressLiveData.postValue(loadedImagesCount);
        scheduleNextLoad();
    }

    private void logResult(long startTime, long duration, boolean success, String details) {
        try {
            TestResult tr = new TestResult("Image Loading", duration, details, success);
            TestEntry entry = new TestEntry(loadedImagesCount, tr, startTime, duration,
                    System.currentTimeMillis() - suiteStartTime);

            if (csvWriter != null) {
                csvWriter.write(entry);
            }
        } catch (Exception e) {
            Log.e(TAG, "Log failed", e);
        }
    }

    private void scheduleNextLoad() {
        mainHandler.postDelayed(this::loadNextImage, 16);
    }

    private void finishBatch() {
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
}
