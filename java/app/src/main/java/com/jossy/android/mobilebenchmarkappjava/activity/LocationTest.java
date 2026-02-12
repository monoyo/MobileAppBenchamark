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
import com.jossy.android.mobilebenchmarkappjava.consts.Config;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter;

import java.io.File;
import java.util.Locale;

public class LocationTest extends AppCompatActivity {
    private static final String TAG = "LocationTestActivity";
    private static final long WATCHDOG_TIMEOUT_MS = 15_000;
    private static final int UI_UPDATE_FREQUENCY = 50; // Update UI every 50 samples

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private BufferedCsvWriter csvWriter;

    private TextView statusText;
    private TextView metricsText;
    private ProgressBar progressBar;

    private int sampleCount = 0;
    private long intervalMs = 1000L;
    private String csvPath;

    private long suiteStartTime;
    private long lastSampleTime;
    private boolean isFinished = false;
    private Location lastKnownLocation;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());

    private final Runnable samplingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinished)
                return;
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
        // Use 1ms interval for high-speed sampling in "Stream+Poll" mode
        intervalMs = getIntent().getIntExtra("interval", 1);
        if (intervalMs > 100) {
            // If legacy interval was passed, override it to 1ms for standardization
            intervalMs = 1L;
        }

        csvPath = getIntent().getStringExtra("csv_path");
        if (progressBar != null) {
            progressBar.setMax(Config.sampleCount);
        }
    }

    private void initCsv() {
        suiteStartTime = System.currentTimeMillis();
        lastSampleTime = suiteStartTime;
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
                if (isFinished)
                    return;
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
        Log.i(TAG, "Starting Benchmark: " + Config.sampleCount + " samples @ " + intervalMs + "ms");

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

            if (sampleCount % UI_UPDATE_FREQUENCY == 0 || sampleCount >= Config.sampleCount) {
                updateUI();
            }

            if (sampleCount >= Config.sampleCount) {
                finishBenchmark();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during sampling", e);
        }
    }

    private TestEntry createTestEntry(long timestamp) {
        long duration = timestamp - lastSampleTime;
        lastSampleTime = timestamp;

        String details = (lastKnownLocation != null)
                ? String.format(Locale.US, "Lat:%.6f,Lon:%.6f,Acc:%.1f",
                        lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(),
                        lastKnownLocation.getAccuracy())
                : "No Signal";

        TestResult result = new TestResult("Location Test", duration, details, lastKnownLocation != null);
        return new TestEntry(sampleCount, result, timestamp, duration, timestamp - suiteStartTime);
    }

    private void updateUI() {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(String.format(Locale.US, "Progress: %d / %d", sampleCount, Config.sampleCount));
            }
            if (progressBar != null) {
                progressBar.setProgress(sampleCount);
            }
            if (metricsText != null) {
                metricsText.setText("System metrics disabled");
            }
        });
    }

    private void logErrorToCsv() {
        if (csvWriter != null) {
            long now = System.currentTimeMillis();
            long duration = now - lastSampleTime;
            lastSampleTime = now;

            TestResult tr = new TestResult("Location Test", duration, "Error: Watchdog Timeout (No GPS Signal)", false);
            csvWriter.write(new TestEntry(sampleCount, tr, now, duration, now - suiteStartTime));
        }
    }

    private void resetWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
        watchdogHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS);
    }

    private void finishBenchmark() {
        if (isFinished)
            return;
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
