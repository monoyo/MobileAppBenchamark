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
    private static final int INITIAL_OBJECT_COUNT = 100;

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

    private int targetFrames = 100; // Default
    private static final int START_OBJECTS = 1000;
    private static final int END_OBJECTS = 50000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        targetFrames = getIntent().getIntExtra("iterations", 100);

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

        initObjects(START_OBJECTS);
        setupCsv();

        container.post(() -> {
            width = container.getWidth();
            height = container.getHeight();
            startTest();
        });
    }

    private void setupCsv() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "benchmarks/" + timestamp);
            String sessionPath = getIntent().getStringExtra("session_dir");
            if (sessionPath != null) {
                dir = new File(sessionPath);
            } else {
                if (!dir.exists())
                    dir.mkdirs();
            }

            File file = new File(dir, "stress_test_java_" + timestamp + ".csv");
            csvWriter = new BufferedWriter(new FileWriter(file));
            csvWriter.write("Frame,ObjectCount,FrameTimeMs,FPS,ElapsedMs\n");
        } catch (IOException e) {
            Log.e(TAG, "Failed to create CSV writer", e);
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
        if (count <= 0)
            return;
        float maxW = width > 0 ? width : 1000;
        float maxH = height > 0 ? height : 2000;

        for (int i = 0; i < count; i++) {
            float size = 50f;
            float x = random.nextFloat() * (maxW - size);
            float y = random.nextFloat() * (maxH - size);

            objects.add(new RectF(x, y, x + size, y + size));
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
        if (!isRunning)
            return;

        long currentNano = System.nanoTime();
        long diffNanos = currentNano - lastFrameTimeNanos;
        lastFrameTimeNanos = currentNano;

        double frameTimeMs = diffNanos / 1_000_000.0;
        double fps = frameTimeMs > 0 ? 1000.0 / frameTimeMs : 0;

        // Log frame
        try {
            if (csvWriter != null) {
                // Time from start in ms
                long elapsed = System.currentTimeMillis() - startTime;
                csvWriter.write(String.format(Locale.US, "%d,%d,%.2f,%.2f,%d\n", frameCount, currentObjectCount,
                        frameTimeMs, fps, elapsed));
            }
        } catch (IOException e) {
            // Ignore
        }

        // Ramp Up Logic - Time Based
        // Animation duration: 100 seconds
        // Logic: 1000 objects start + 1000 new every second
        long elapsed = System.currentTimeMillis() - startTime;

        int secondsElapsed = (int) (elapsed / 1000);
        int desiredObjects = 1000 + (secondsElapsed * 1000);

        // Ensure strictly adding
        if (desiredObjects > currentObjectCount) {
            int toAdd = desiredObjects - currentObjectCount;
            addObjects(toAdd);
        }

        // Render & Update
        updatePositions();
        stressTestView.setObjects(objects, colors);

        // Update UI
        infoText.setText(String.format(Locale.US, "Time: %ds / 100s\nObjects: %d\nFPS: %.1f",
                secondsElapsed, currentObjectCount, fps));

        frameCount++;

        // Stop Condition - 100 seconds
        if (elapsed >= 100_000) {
            finishTest(true);
        } else {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void updatePositions() {
        if (width == 0)
            return;

        for (int i = 0; i < objects.size(); i++) {
            RectF rect = objects.get(i);
            float vx = velocitiesX.get(i);
            float vy = velocitiesY.get(i);

            rect.offset(vx, vy);

            // Bounce
            if (rect.left < 0 || rect.right > width) {
                velocitiesX.set(i, -vx);
                rect.offset(-vx * 2, 0);
            }
            if (rect.top < 0 || rect.bottom > height) {
                velocitiesY.set(i, -vy);
                rect.offset(0, -vy * 2);
            }
        }
    }

    private void finishTest(boolean success) {
        isRunning = false;
        long duration = System.currentTimeMillis() - startTime;

        try {
            if (csvWriter != null) {
                csvWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        TestResult result = new TestResult(
                "UI Stress Test",
                duration,
                String.format(Locale.US, "Max Objects: %d, Frames: %d", currentObjectCount, frameCount),
                success);

        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}
