package com.jossy.android.mobilebenchmarkappjava.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter;
import com.jossy.android.mobilebenchmarkappjava.metrics.SystemMetricsCollector;

import java.io.File;
import java.util.Locale;

public class LocationTestActivity extends AppCompatActivity {
    private static final String TAG = "LocationTestActivity";
    private static final long WATCHDOG_TIMEOUT_MS = 15_000;
    private static final int UI_UPDATE_FREQUENCY = 50; // Update UI every 50 samples

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private SystemMetricsCollector metricsCollector;
    private BufferedCsvWriter csvWriter;

    private TextView statusText;
    private TextView metricsText;
    private ProgressBar progressBar;

    private int sampleCount = 0;
    private final int TARGET_SAMPLES = 10000;
    private long intervalMs = 1000L;
    private String csvPath;
    
    private long suiteStartTime;
    private boolean isFinished = false;
    private Location lastKnownLocation;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());

    private final Runnable samplingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinished) return;
            performSample();
            mainHandler.postDelayed(this, intervalMs);
        }
    };

    private final Runnable watchdogRunnable = () -> {
        if (!isFinished) {
            Log.e(TAG, "Watchdog: No location updates received for " + WATCHDOG_TIMEOUT_MS + "ms");
            logErrorToCsv();
            finishBenchmark();
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_test);

        initViews();
        parseIntent();
        initMetrics();
        initCsv();
        initLocationClient();
        
        startBenchmark();
    }

    private void initViews() {
        statusText = findViewById(R.id.locationStatus);
        metricsText = findViewById(R.id.metricsText);
        progressBar = findViewById(R.id.testProgress);
    }

    private void parseIntent() {
        intervalMs = getIntent().getIntExtra("interval", 1000);
        csvPath = getIntent().getStringExtra("csv_path");
        if (progressBar != null) {
            progressBar.setMax(TARGET_SAMPLES);
        }
    }

    private void initMetrics() {
        File outputDir = getExternalFilesDir(null);
        metricsCollector = new SystemMetricsCollector(this, outputDir, "location_test_" + System.currentTimeMillis(), 500);
        try {
            metricsCollector.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start metrics collector", e);
        }
    }

    private void initCsv() {
        suiteStartTime = System.currentTimeMillis();
        String path = csvPath != null ? csvPath : getFilesDir() + "/location_benchmark_" + suiteStartTime + ".csv";
        try {
            // Buffer capacity 500 for high volume
            csvWriter = new BufferedCsvWriter(new File(path), 500, 128 * 1024);
            csvWriter.initialize();
        } catch (Exception e) {
            Log.e(TAG, "CSV Writer initialization failed", e);
        }
    }

    private void initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (isFinished) return;
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastKnownLocation = location;
                    resetWatchdog();
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void startBenchmark() {
        Log.i(TAG, "Starting Benchmark: " + TARGET_SAMPLES + " samples @ " + intervalMs + "ms");
        
        LocationRequest locationRequest = new LocationRequest.Builder(1000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(500)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        mainHandler.postDelayed(samplingRunnable, intervalMs);
        resetWatchdog();
    }

    private void performSample() {
        try {
            long now = System.currentTimeMillis();
            TestEntry entry = createTestEntry(now);
            
            if (csvWriter != null) {
                csvWriter.write(entry);
            }

            sampleCount++;
            
            if (sampleCount % UI_UPDATE_FREQUENCY == 0 || sampleCount >= TARGET_SAMPLES) {
                updateUI();
            }

            if (sampleCount >= TARGET_SAMPLES) {
                finishBenchmark();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during sampling", e);
        }
    }

    private TestEntry createTestEntry(long timestamp) {
        String details = (lastKnownLocation != null) 
                ? String.format(Locale.US, "Lat:%.6f,Lon:%.6f,Acc:%.1f", 
                    lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), lastKnownLocation.getAccuracy())
                : "No Signal";
        
        TestResult result = new TestResult("Location Test", 0, details, lastKnownLocation != null);
        return new TestEntry(sampleCount, result, timestamp, 0, timestamp - suiteStartTime);
    }

    private void updateUI() {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(String.format(Locale.US, "Progress: %d / %d", sampleCount, TARGET_SAMPLES));
            }
            if (progressBar != null) {
                progressBar.setProgress(sampleCount);
            }
            if (metricsText != null && metricsCollector != null) {
                metricsText.setText(String.format(Locale.US, "CPU: %.1f%% | RAM: %.1f MB", 
                        metricsCollector.getLastCpuUsage(), metricsCollector.getLastMemoryUsage()));
            }
        });
        
        if (metricsCollector != null) {
            metricsCollector.setCurrentTest("Location", sampleCount);
        }
    }

    private void logErrorToCsv() {
        if (csvWriter != null) {
            long now = System.currentTimeMillis();
            TestResult tr = new TestResult("Location Test", 0, "Error: Watchdog Timeout (No GPS Signal)", false);
            csvWriter.write(new TestEntry(sampleCount, tr, now, 0, now - suiteStartTime));
        }
    }

    private void resetWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS);
    }

    private void finishBenchmark() {
        if (isFinished) return;
        isFinished = true;

        cleanup();

        long totalTime = System.currentTimeMillis() - suiteStartTime;
        TestResult result = new TestResult("Location Test", totalTime, 
                "Completed " + sampleCount + " samples", sampleCount > 0);
        
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void cleanup() {
        watchdogHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (metricsCollector != null) {
            metricsCollector.stop();
        }

        if (csvWriter != null) {
            csvWriter.close();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinished && !isChangingConfigurations()) {
            finishBenchmark();
        }
    }

    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}
