package com.jossy.android.mobilebenchmarkappjava;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends BaseActivity {
    private final long startTime = System.currentTimeMillis();
    private boolean hasAppLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate called");

        // Enable hardware acceleration
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        
        // Optimize window flags
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );
        
        setContentView(R.layout.activity_main);
        Log.d("MainActivity", "Content view set");

        // Optimize view rendering
        findViewById(android.R.id.content).setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);

        Button startBenchmarkButton = findViewById(R.id.btnStartBenchmark);
        startBenchmarkButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BenchmarkActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });
        
        Button startImageTestButton = findViewById(R.id.btnStartImageTest);
        startImageTestButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ImageLoadingActivity.class);
            startActivity(intent);
        });

        Button startApiTestButton = findViewById(R.id.btnStartApiTest);
        startApiTestButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ApiTestActivity.class);
            startActivity(intent);
        });

        Button startLocationTestButton = findViewById(R.id.btnStartLocationTest);
        startLocationTestButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LocationTestActivity.class);
            startActivity(intent);
        });

        Button startSuiteButton = findViewById(R.id.btnStartSuite);
        startSuiteButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BenchmarkSuiteActivity.class);
            startActivity(intent);
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (!hasAppLaunched) {
            hasAppLaunched = true;
            long elapsedTime = System.currentTimeMillis() - startTime;
            TextView launchTime = findViewById(R.id.appLaunchTime);
            launchTime.setText("App Launch Time: " + elapsedTime + "ms");
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear any pending animations
        overridePendingTransition(0, 0);
    }
}
