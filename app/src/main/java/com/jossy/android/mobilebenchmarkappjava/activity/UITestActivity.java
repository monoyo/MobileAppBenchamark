package com.jossy.android.mobilebenchmarkappjava.activity;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

public class UITestActivity extends AppCompatActivity {
    private FrameLayout container;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark);
        container = findViewById(R.id.container);

        Log.d("UITestActivity", "Starting UI test");
        startTime = System.currentTimeMillis();
        startUITest();
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

        // Give time for animations to be visible
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d("UITestActivity", "UI test completed");
            long executionTime = System.currentTimeMillis() - startTime;
            TestResult result = new TestResult("UI Test", executionTime, "Animation frames rendered", true);
            Intent intent = new Intent();
            intent.putExtra(BenchmarkApplication.RESULT, result);
            setResult(RESULT_OK, intent);
            finish();
        }, 5000); // Wait 5 seconds to show animations
    }
}
