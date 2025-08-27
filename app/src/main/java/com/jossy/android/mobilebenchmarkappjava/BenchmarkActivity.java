package com.jossy.android.mobilebenchmarkappjava;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class BenchmarkActivity extends AppCompatActivity {
    private FrameLayout container;
    private int frameCount = 0;
    private long startTime = 0L;
    private int lastFps = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("BenchmarkActivity", "onCreate called");
        startFPSCounter();
        setContentView(R.layout.activity_benchmark);
        container = findViewById(R.id.container);
        new Thread(() -> {
            Log.d("BenchmarkActivity", "Starting UI test");
            runOnUiThread(this::startUITest);
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Log.e("BenchmarkActivity", "Error during sleep", e);
            }
            runOnUiThread(() -> {
                Log.d("BenchmarkActivity", "UI test ended");
                container.removeAllViews();
                container.setBackgroundColor(Color.WHITE);
                TextView textView = new TextView(BenchmarkActivity.this);
                textView.setText("Test UI ended. AVG FPS: " + lastFps);
                textView.setTextColor(Color.BLACK);
                textView.setTextSize(20f);
                container.addView(textView);
                FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                layout.setMargins(16, 16, 16, 16);
                textView.setLayoutParams(
                        layout
                );
            });
            long cpuStart = System.currentTimeMillis();
            Log.d("BenchmarkActivity", "Starting CPU test");
            for (int i = 0; i < 7; i++) {
                new Thread(CPUTest::runBenchmark).start();
            }
            CPUTest.runBenchmark();
            long cpuElapsed = System.currentTimeMillis() - cpuStart;
            Log.i("BenchmarkActivity", "CPU test time: " + cpuElapsed + "ms");
            runOnUiThread(() -> {
                TextView textView = new TextView(BenchmarkActivity.this);
                textView.setText("Test CPU ended. Time: " + cpuElapsed + " ms");
                textView.setTextColor(Color.BLACK);
                textView.setTextSize(20f);
                container.addView(textView);
                FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                layout.setMargins(16, 16, 16, 16);
                textView.setPadding(0, 80, 0, 0);
                textView.setLayoutParams(
                        layout
                );
            });
            long ramStart = System.currentTimeMillis();
            Log.d("BenchmarkActivity", "Starting RAM test");
            RAMTest.runBenchmark();
            long ramElapsed = System.currentTimeMillis() - ramStart;
            Log.i("BenchmarkActivity", "RAM test time: " + ramElapsed + "ms");
            runOnUiThread(() -> {
                TextView textView = new TextView(BenchmarkActivity.this);
                textView.setText("Test RAM ended. Time: " + ramElapsed + " ms");
                textView.setTextColor(Color.BLACK);
                textView.setTextSize(20f);
                container.addView(textView);
                FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                layout.setMargins(16, 16, 16, 16);
                textView.setPadding(0, 160, 0, 0);
                textView.setLayoutParams(
                        layout
                );
            });

            // Notify BenchmarkSuiteActivity that the test is complete
            setResult(RESULT_OK);
            finish();
        }).start();
    }

    private void startUITest() {
        int size = 50;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        Random random = new Random();

        for (int i = 0; i < 1500; i++) {
            View view = new View(this);
            view.setBackgroundColor(Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(size, size);
            view.setLayoutParams(layoutParams);
            view.setX(random.nextInt(screenWidth - size));
            view.setY(random.nextInt(screenHeight - size));

            container.addView(view);

            ObjectAnimator animX = ObjectAnimator.ofFloat(view, "translationX", view.getX(), view.getX() + random.nextInt(400) - 200, view.getX());
            ObjectAnimator animY = ObjectAnimator.ofFloat(view, "translationY", view.getY(), view.getY() + random.nextInt(400) - 200, view.getY());

            animX.setRepeatCount(ObjectAnimator.INFINITE);
            animY.setRepeatCount(ObjectAnimator.INFINITE);
            animX.setDuration(2000L);
            animY.setDuration(2000L);

            animX.start();
            animY.start();
        }
    }

    private void startFPSCounter() {
        startTime = System.nanoTime();
        frameCount = 0;

        Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                frameCount++;
                double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
                if (elapsedSeconds >= 5.0) {
                    int fps = (int) (frameCount / elapsedSeconds);
                    lastFps = fps;
                    Log.d("BenchmarkActivity", "🔧 AVG FPS: " + fps);
                } else {
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        };

        Choreographer.getInstance().postFrameCallback(frameCallback);
    }
}