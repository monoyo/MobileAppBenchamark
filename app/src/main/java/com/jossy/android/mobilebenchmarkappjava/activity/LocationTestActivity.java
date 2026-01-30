package com.jossy.android.mobilebenchmarkappjava.activity;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

public class LocationTestActivity extends AppCompatActivity {
    private static final long UPDATE_INTERVAL = 1000L;
    private static final long FASTEST_UPDATE_INTERVAL = 500L;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private long startTime;

    @RequiresPermission(allOf = { Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION })
    private int sampleCount = 0;
    private int targetSamples = 0;
    private long suiteStartTime;
    private boolean isFinished = false;
    private String csvPath;
    private com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter csvWriter;

    private android.widget.TextView statusText;
    private long intervalMs = 1000L;
    private Location lastKnownLocation;
    private final Handler samplingHandler = new Handler(Looper.getMainLooper());
    private final Runnable samplingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinished)
                return;
            sampleLocation();
            samplingHandler.postDelayed(this, intervalMs);
        }
    };

    @RequiresPermission(allOf = { Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION })
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_test);

        statusText = findViewById(R.id.locationStatus);

        targetSamples = getIntent().getIntExtra("iterations", 5);
        intervalMs = getIntent().getIntExtra("interval", 1000); // Default 1s
        csvPath = getIntent().getStringExtra("csv_path");

        Log.i("LocationTestActivity", "Starting Location Batch: " + targetSamples + " @ " + intervalMs + "ms");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupCsv();
        setupLocationCallback();
        startLocationUpdates();

        // Start sampling loop
        samplingHandler.postDelayed(samplingRunnable, intervalMs);
    }

    private void setupCsv() {
        suiteStartTime = System.currentTimeMillis();
        try {
            csvWriter = new com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter(
                    new java.io.File(csvPath != null ? csvPath : getFilesDir() + "/temp_location.csv"), 1000,
                    64 * 1024);
            csvWriter.initialize();
        } catch (Exception e) {
            Log.e("LocationTestActivity", "Writer init failed", e);
        }
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (isFinished)
                    return;

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastKnownLocation = location;

                    // Reset watchdog on valid update
                    timeoutHandler.removeCallbacksAndMessages(null);
                    timeoutHandler.postDelayed(timeoutRunnable, TEST_TIMEOUT_MS);
                }
            }
        };
    }

    private void sampleLocation() {
        try {
            long eventTime = System.currentTimeMillis();
            long duration = eventTime - startTime;

            try {
                String details = (lastKnownLocation != null) ? "Lat: " + lastKnownLocation.getLatitude() : "No Signal";
                com.jossy.android.mobilebenchmarkappjava.data.TestResult tr = new com.jossy.android.mobilebenchmarkappjava.data.TestResult(
                        "Location Test", 0, details, lastKnownLocation != null);

                com.jossy.android.mobilebenchmarkappjava.data.TestEntry entry = new com.jossy.android.mobilebenchmarkappjava.data.TestEntry(
                        sampleCount, tr, eventTime, 0, eventTime - suiteStartTime);

                if (csvWriter != null)
                    csvWriter.write(entry);

            } catch (Exception e) {
                Log.e("LocationTestActivity", "Log failed", e);
            }

            sampleCount++;

            if (statusText != null) {
                runOnUiThread(() -> statusText.setText("Location Test: " + sampleCount + " / " + targetSamples));
            }

            if (sampleCount >= targetSamples) {
                finishBatch();
            }

        } catch (Exception e) {
            Log.e("LocationTestActivity", "Critical error in sampleLocation", e);
        }
    }

    // Unused now but kept for compatibility logic if needed
    // private void processLocation(Location location) { ... }

    private final android.os.Handler timeoutHandler = new android.os.Handler(Looper.getMainLooper());
    private static final long TEST_TIMEOUT_MS = 15_000; // 15 seconds timeout if stuck

    @RequiresPermission(allOf = { Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION })
    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(1000) // Request 1Hz updates from GPS
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(500)
                .build();

        startTime = System.currentTimeMillis();

        // Safety timeout - watchdog
        timeoutHandler.postDelayed(timeoutRunnable, TEST_TIMEOUT_MS);

        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback, Looper.getMainLooper());
    }

    private final Runnable timeoutRunnable = () -> {
        if (!isFinished) {
            Log.e("LocationTestActivity",
                    "Test Timed Out - No location updates received for " + TEST_TIMEOUT_MS + "ms.");
            try {
                if (csvWriter != null) {
                    com.jossy.android.mobilebenchmarkappjava.data.TestResult tr = new com.jossy.android.mobilebenchmarkappjava.data.TestResult(
                            "Location Test", 0, "Error: Timeout (No GPS Signal)", false);
                    com.jossy.android.mobilebenchmarkappjava.data.TestEntry entry = new com.jossy.android.mobilebenchmarkappjava.data.TestEntry(
                            sampleCount, tr, System.currentTimeMillis(), 0,
                            System.currentTimeMillis() - suiteStartTime);
                    csvWriter.write(entry);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            finishBatch();
        }
    };

    private void stopLocationUpdates() {
        timeoutHandler.removeCallbacksAndMessages(null);
        samplingHandler.removeCallbacksAndMessages(null); // Stop sampling

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void finishBatch() {
        if (isFinished)
            return;
        isFinished = true;

        timeoutHandler.removeCallbacksAndMessages(null);
        samplingHandler.removeCallbacksAndMessages(null);

        stopLocationUpdates();

        long totalTime = System.currentTimeMillis() - suiteStartTime;
        try {
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        TestResult result = new TestResult("Location Test", totalTime, "Batch completed: " + sampleCount,
                sampleCount > 0);
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinished) {
            stopLocationUpdates();
            finish(); // Kill if paused mid-test
        }
    }
}
