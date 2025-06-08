package com.jossy.android.mobilebenchmarkappjava;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private final long startTime = System.currentTimeMillis();
    private boolean hasAppLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startBenchmarkButton = findViewById(R.id.btnStartBenchmark);
        startBenchmarkButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BenchmarkActivity.class);
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
}
