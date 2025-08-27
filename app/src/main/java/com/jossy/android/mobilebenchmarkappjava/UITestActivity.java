package com.jossy.android.mobilebenchmarkappjava;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Random;

public class UITestActivity extends AppCompatActivity {
    private FrameLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark);
        container = findViewById(R.id.container);

        Log.d("UITestActivity", "Starting UI test");
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

        Log.d("UITestActivity", "UI test completed");
        setResult(RESULT_OK);
        finish();
    }
}
