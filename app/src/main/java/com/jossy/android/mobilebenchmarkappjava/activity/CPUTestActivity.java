package com.jossy.android.mobilebenchmarkappjava.activity;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.jossy.android.mobilebenchmarkappjava.CPUTest;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.CpuResult;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import android.content.Intent;

import java.util.concurrent.Executors;

/**
 * Aktywność wykonująca test CPU.
 * 
 * Obsługuje dwa tryby:
 * 1. Iteration-based (gdy przekazano cpu_iterations) - nowa metoda
 * 2. Time-based (fallback) - kompatybilność wsteczna
 */
public class CPUTestActivity extends AppCompatActivity {

    private static final String TAG = "CPUTestActivity";
    private static final long DEFAULT_CPU_ITERATIONS = 1_000_000L;
    private static final long DEFAULT_DURATION_MS = 3000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cpu_test);

        Log.d(TAG, "Starting CPU test");
        startCPUTest();
    }

    private void startCPUTest() {
        final long cpuIterations = getIntent().getLongExtra("cpu_iterations", 1000);
        final int targetSamples = getIntent().getIntExtra("iterations", 1);
        final String csvPath = getIntent().getStringExtra("csv_path");

        Log.i(TAG, "Starting CPU Batch Test: samples=" + targetSamples + ", cpu_iters=" + cpuIterations);

        Executors.newSingleThreadExecutor().execute(() -> {
            long suiteStart = System.currentTimeMillis();

            // Initializing CSV Writer
            try (com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter writer = new com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter(
                    new java.io.File(csvPath), 1000, 64 * 1024)) {

                writer.initialize();

                for (int i = 0; i < targetSamples; i++) {
                    long start = System.currentTimeMillis();
                    CpuResult r = CPUTest.runBenchmarkIterations(cpuIterations, null);
                    long duration = System.currentTimeMillis() - start;

                    // Manually creating TestEntry-like CSV line or using shared objects?
                    // Better to re-use TestEntry structure logic or write directly.
                    // For speed in batch, let's write directly if possible, or use the writer's
                    // method.
                    // The BufferedCsvWriter expects TestEntry. Let's create it.

                    TestResult tr = new TestResult("CPU Test", duration, "threads=" + r.threads, true);
                    TestEntry entry = new TestEntry(i, tr, start, duration, System.currentTimeMillis() - suiteStart);
                    writer.write(entry);
                }
                writer.flush();

            } catch (Exception e) {
                Log.e(TAG, "Batch CPU Test failed", e);
                runOnUiThread(() -> {
                    TestResult result = new TestResult("CPU Test", 0, "Error: " + e.getMessage(), false);
                    returnResult(result);
                });
                return;
            }

            long totalTime = System.currentTimeMillis() - suiteStart;
            Log.i(TAG, "CPU Batch completed in " + totalTime + "ms");

            runOnUiThread(() -> {
                TestResult result = new TestResult("CPU Test", totalTime,
                        "Batch completed: " + targetSamples + " samples", true);
                returnResult(result);
            });
        });
    }

    private void returnResult(TestResult result) {
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}
