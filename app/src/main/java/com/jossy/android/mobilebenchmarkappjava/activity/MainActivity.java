package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.jossy.android.mobilebenchmarkappjava.R;

public class MainActivity extends BaseActivity {
    private final long startTime = System.currentTimeMillis();
    private boolean hasAppLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate called");
        
        setContentView(R.layout.activity_main);
        Log.d("MainActivity", "Content view set");

        // Optimize view rendering
        findViewById(android.R.id.content).setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);

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
