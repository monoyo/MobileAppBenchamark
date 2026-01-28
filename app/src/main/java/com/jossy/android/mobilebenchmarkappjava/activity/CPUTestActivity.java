package com.jossy.android.mobilebenchmarkappjava.activity;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.jossy.android.mobilebenchmarkappjava.CPUTest;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.CpuResult;
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
        // Pobierz parametr iteracji z intenta (jeśli przekazany)
        final long cpuIterations = getIntent().getLongExtra("cpu_iterations", 0);
        final boolean useIterationMode = cpuIterations > 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            long cpuStart = System.currentTimeMillis();
            CpuResult r;

            try {
                if (useIterationMode) {
                    // Nowy tryb: iteration-based
                    Log.i(TAG, "Running iteration-based CPU test: " + cpuIterations + " iterations");
                    r = CPUTest.runBenchmarkIterations(cpuIterations, null);
                } else {
                    // Tryb kompatybilności: time-based
                    Log.i(TAG, "Running time-based CPU test: " + DEFAULT_DURATION_MS + "ms");
                    r = CPUTest.runBenchmarkParallel(DEFAULT_DURATION_MS, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "CPU test failed: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    TestResult result = new TestResult(
                            "CPU Test",
                            System.currentTimeMillis() - cpuStart,
                            "error=" + e.getMessage(),
                            false);
                    returnResult(result);
                });
                return;
            }

            long cpuElapsed = System.currentTimeMillis() - cpuStart;
            Log.i(TAG, String.format("CPU test completed: %dms, threads=%d, iters=%d",
                    cpuElapsed, r.threads, r.iterations));

            runOnUiThread(() -> {
                String details = String.format("threads=%d, iterations=%d, mode=%s",
                        r.threads, r.iterations, useIterationMode ? "iteration" : "time");

                TestResult result = new TestResult(
                        "CPU Test",
                        cpuElapsed,
                        details,
                        true);
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
