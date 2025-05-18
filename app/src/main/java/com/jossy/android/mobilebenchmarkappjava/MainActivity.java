package com.jossy.android.mobilebenchmarkappjava;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private TextView startupTimeView;
    private TextView executionTimeView;
    private TextView ramUsageView;
    private TextView cpuUsageView;
    private TextView uiLatencyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long startTime = SystemClock.elapsedRealtime();
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        startupTimeView = findViewById(R.id.startupTime);
        executionTimeView = findViewById(R.id.executionTime);
        ramUsageView = findViewById(R.id.ramUsage);
        cpuUsageView = findViewById(R.id.cpuUsage);
        uiLatencyView = findViewById(R.id.uiLatency);

        long loadedTime = SystemClock.elapsedRealtime();
        long startupTime = loadedTime - startTime;
        startupTimeView.setText("Czas uruchomienia: " + startupTime + "ms");

        Button btnExecution = findViewById(R.id.btnExecution);
        Button btnSimulateUsage = findViewById(R.id.btnSimulateUsage);
        Button btnUILatency = findViewById(R.id.btnUILatency);

        btnExecution.setOnClickListener(v -> measureExecutionTime());
        btnSimulateUsage.setOnClickListener(v -> simulateRamCpuUsage());
        btnUILatency.setOnClickListener(v -> measureUiLatency());
    }

    private void measureExecutionTime() {
        long start = SystemClock.elapsedRealtime();
        for (int i = 0; i <= 100000; i++) {
            double x = (double) i;
            double result = Math.sqrt(x * x + x) * Math.log(x + 1);
        }
        long end = SystemClock.elapsedRealtime();
        long duration = end - start;
        executionTimeView.setText("Czas wykonania operacji: " + duration + "ms");
    }

    private void simulateRamCpuUsage() {
        double ram = 100 + new Random().nextDouble() * 100;
        double cpu = 10 + new Random().nextDouble() * 50;
        ramUsageView.setText(String.format("Zużycie RAM: %.1f MB", ram));
        cpuUsageView.setText(String.format("Zużycie CPU: %.1f %%", cpu));
    }

    private void measureUiLatency() {
        long start = SystemClock.uptimeMillis();
        new Handler(Looper.getMainLooper()).post(() -> {
            long end = SystemClock.uptimeMillis();
            long latency = end - start;
            uiLatencyView.setText("Latencja UI: " + latency + "ms");
        });
    }
}