package com.jossy.android.mobilebenchmarkappjava.activity;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.RAMTest;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

import java.util.concurrent.Executors;

public class RAMTestActivity extends AppCompatActivity {

    private android.widget.TextView statusText;
    private long lastUiUpdate = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ram_test);

        statusText = findViewById(R.id.cpuStatus); // Usage of existing ID from layout

        Log.d("RAMTestActivity", "Starting RAM test");
        startRAMTest();
    }

    private void startRAMTest() {
        final int targetSamples = getIntent().getIntExtra("iterations", 1);
        final String csvPath = getIntent().getStringExtra("csv_path");

        // Adaptive runs per sample: increased load
        final int runsPerSample = targetSamples > 1000 ? 50 : 500;

        Log.i("RAMTestActivity", "Starting RAM Batch: samples=" + targetSamples + ", runs/sample=" + runsPerSample);
        statusText.setText("Initializing RAM Batch...");

        Executors.newSingleThreadExecutor().execute(() -> {
            long suiteStart = System.currentTimeMillis();

            try (com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter writer = new com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter(
                    new java.io.File(csvPath != null ? csvPath : getFilesDir() + "/temp_ram.csv"), 1000, 64 * 1024)) {

                writer.initialize();

                for (int i = 0; i < targetSamples; i++) {
                    long start = System.currentTimeMillis();
                    RAMTest.runBenchmark(runsPerSample);
                    long duration = System.currentTimeMillis() - start;

                    com.jossy.android.mobilebenchmarkappjava.data.TestResult tr = new com.jossy.android.mobilebenchmarkappjava.data.TestResult(
                            "RAM Test", duration, "RAM benchmark", true);
                    com.jossy.android.mobilebenchmarkappjava.data.TestEntry entry = new com.jossy.android.mobilebenchmarkappjava.data.TestEntry(
                            i, tr, start, duration, System.currentTimeMillis() - suiteStart);
                    writer.write(entry);

                    if (targetSamples <= 1000 || i % (targetSamples / 100) == 0) {
                        int finalI = i;
                        runOnUiThread(() -> statusText.setText("RAM Test: " + (finalI + 1) + " / " + targetSamples));
                    }
                }
                writer.flush();

            } catch (Exception e) {
                Log.e("RAMTestActivity", "Batch RAM Test failed", e);
                runOnUiThread(() -> {
                    statusText.setText("Error: " + e.getMessage());
                    com.jossy.android.mobilebenchmarkappjava.data.TestResult result = new com.jossy.android.mobilebenchmarkappjava.data.TestResult(
                            "RAM Test", 0, "Error: " + e.getMessage(), false);
                    returnResult(result);
                });
                return;
            }

            long totalTime = System.currentTimeMillis() - suiteStart;
            Log.i("RAMTestActivity", "RAM Batch completed in " + totalTime + "ms");

            runOnUiThread(() -> {
                com.jossy.android.mobilebenchmarkappjava.data.TestResult result = new com.jossy.android.mobilebenchmarkappjava.data.TestResult(
                        "RAM Test", totalTime, "Batch completed: " + targetSamples + " samples", true);
                returnResult(result);
            });
        });
    }

    private void returnResult(com.jossy.android.mobilebenchmarkappjava.data.TestResult result) {
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}
