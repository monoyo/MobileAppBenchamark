package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.view.Choreographer;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.view.StressTestView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class GPUTestActivity extends AppCompatActivity implements Choreographer.FrameCallback {

    private static final String TAG = "StressTestActivity";
    private static final int INITIAL_OBJECT_COUNT = 250;
    private static final int OBJECT_INCREMENT_PER_SECOND = 250;
    private static final int TARGET_SAMPLES = 10000;
    private static final float OBJECT_SIZE = 50f;

    private StressTestView stressTestView;
    private TextView infoText;
    private FrameLayout container;

    private boolean isRunning = false;
    private long startTime;
    private long lastFrameTimeNanos;
    private int currentObjectCount = INITIAL_OBJECT_COUNT;

    // Data for rendering
    private final List<RectF> objects = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private final List<Float> velocitiesX = new ArrayList<>();
    private final List<Float> velocitiesY = new ArrayList<>();
    private final Random random = new Random();

    // Screen bounds
    private int width;
    private int height;

    // Logging
    private BufferedWriter csvWriter;
    private long frameCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initUI();
        initObjects(INITIAL_OBJECT_COUNT);
        setupCsv();

        container.post(() -> {
            width = container.getWidth();
            height = container.getHeight();
            startTest();
        });
    }

    private void initUI() {
        container = new FrameLayout(this);
        stressTestView = new StressTestView(this);
        infoText = new TextView(this);
        infoText.setTextColor(Color.BLACK);
        infoText.setTextSize(16);
        infoText.setPadding(20, 20, 20, 20);
        infoText.setBackgroundColor(Color.argb(150, 255, 255, 255));

        container.addView(stressTestView);
        container.addView(infoText);
        setContentView(container);
    }

    private void setupCsv() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = getBenchmarkDirectory(timestamp);

            File file = new File(dir, "stress_test_java_" + timestamp + ".csv");
            csvWriter = new BufferedWriter(new FileWriter(file));
            csvWriter.write("Frame,ObjectCount,FrameTimeMs,FPS,ElapsedMs\n");
        } catch (IOException e) {
            Log.e(TAG, "Failed to create CSV writer", e);
        }
    }

    private File getBenchmarkDirectory(String timestamp) {
        String sessionPath = getIntent().getStringExtra("session_dir");
        if (sessionPath != null) {
            return new File(sessionPath);
        } else {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "benchmarks/" + timestamp);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return dir;
        }
    }

    private void initObjects(int count) {
        objects.clear();
        colors.clear();
        velocitiesX.clear();
        velocitiesY.clear();
        currentObjectCount = 0;
        addObjects(count);
    }

    private void addObjects(int count) {
        if (count <= 0) return;

        float maxW = width > 0 ? width : 1000;
        float maxH = height > 0 ? height : 2000;

        for (int i = 0; i < count; i++) {
            float x = random.nextFloat() * (maxW - OBJECT_SIZE);
            float y = random.nextFloat() * (maxH - OBJECT_SIZE);

            objects.add(new RectF(x, y, x + OBJECT_SIZE, y + OBJECT_SIZE));
            colors.add(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            velocitiesX.add((random.nextFloat() - 0.5f) * 20);
            velocitiesY.add((random.nextFloat() - 0.5f) * 20);
        }
        currentObjectCount += count;
    }

    private void startTest() {
        isRunning = true;
        startTime = System.currentTimeMillis();
        lastFrameTimeNanos = System.nanoTime();
        frameCount = 0;

        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!isRunning) return;

        long currentNano = System.nanoTime();
        double frameTimeMs = (currentNano - lastFrameTimeNanos) / 1_000_000.0;
        lastFrameTimeNanos = currentNano;
        double fps = frameTimeMs > 0 ? 1000.0 / frameTimeMs : 0;

        long elapsedMs = System.currentTimeMillis() - startTime;

        logFrameData(frameTimeMs, fps, elapsedMs);
        manageObjectCount(elapsedMs);
        updatePositions();
        updateUI(fps, elapsedMs);

        frameCount++;

        if (frameCount >= TARGET_SAMPLES) {
            finishTest(true);
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void logFrameData(double frameTimeMs, double fps, long elapsedMs) {
        try {
            if (csvWriter != null) {
                csvWriter.write(String.format(Locale.US, "%d,%d,%.2f,%.2f,%d\n",
                        frameCount, currentObjectCount, frameTimeMs, fps, elapsedMs));
            }
        } catch (IOException e) {
            Log.e(TAG, "CSV write failed", e);
        }
    }

    private void manageObjectCount(long elapsedMs) {
        int secondsElapsed = (int) (elapsedMs / 1000);
        int desiredObjects = INITIAL_OBJECT_COUNT + (secondsElapsed * OBJECT_INCREMENT_PER_SECOND);

        if (desiredObjects > currentObjectCount) {
            addObjects(desiredObjects - currentObjectCount);
        }
    }

    private void updateUI(double fps, long elapsedMs) {
        stressTestView.setObjects(objects, colors);
        infoText.setText(String.format(Locale.US, "Samples: %d / %d\nObjects: %d\nFPS: %.1f",
                frameCount, TARGET_SAMPLES, currentObjectCount, fps));
    }

    private void updatePositions() {
        if (width == 0) return;

        for (int i = 0; i < objects.size(); i++) {
            RectF rect = objects.get(i);
            float vx = velocitiesX.get(i);
            float vy = velocitiesY.get(i);

            rect.offset(vx, vy);

            handleBoundsCollision(i, rect, vx, vy);
        }
    }

    private void handleBoundsCollision(int index, RectF rect, float vx, float vy) {
        if (rect.left < 0 || rect.right > width) {
            velocitiesX.set(index, -vx);
            rect.offset(-vx * 2, 0);
        }
        if (rect.top < 0 || rect.bottom > height) {
            velocitiesY.set(index, -vy);
            rect.offset(0, -vy * 2);
        }
    }

    private void finishTest(boolean success) {
        isRunning = false;
        long duration = System.currentTimeMillis() - startTime;

        closeCsvWriter();

        TestResult result = new TestResult(
                "UI Stress Test",
                duration,
                String.format(Locale.US, "Max Objects: %d, Samples: %d", currentObjectCount, frameCount),
                success);

        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void closeCsvWriter() {
        try {
            if (csvWriter != null) {
                csvWriter.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close CSV writer", e);
        }
    }
}
