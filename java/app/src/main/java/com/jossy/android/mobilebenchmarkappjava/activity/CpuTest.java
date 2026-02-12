package com.jossy.android.mobilebenchmarkappjava.activity;

import android.annotation.SuppressLint;
import android.util.Log;

import com.jossy.android.mobilebenchmarkappjava.CPUTest;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.consts.Config;
import com.jossy.android.mobilebenchmarkappjava.data.CpuResult;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

public class CpuTest extends BaseTestActivity {

    private static final String TAG = "CpuTest";

    @Override
    protected void initializeActivity() {
        setContentView(R.layout.activity_cpu_test);
        statusText = findViewById(R.id.cpuStatus);
    }

    @Override
    protected void onDestroy() {
        CPUTest.shutdown();
        super.onDestroy();
    }

    @Override
    protected void executeBenchmark() {
        new Thread(() -> {
            CPUTest.initialize(null); // Initialize thread pool
            try {
                initializeCsvWriter();
            } catch (Exception e) {
                Log.e(TAG, "Failed to init CSV", e);
                return;
            }

            long startTime = System.currentTimeMillis();
            updateStatus("Starting CPU benchmark...");

            for (int i = 0; i < Config.sampleCount; i++) {
                // if (executorService.isShutdown()) return;

                int currentIteration = i + 1;
                final CpuResult result = executeSingleIteration(currentIteration);

                runOnUiThread(() -> {
                    updateStatus(getProgressDisplayText(currentIteration));
                });
            }

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;

            CPUTest.shutdown(); // Cleanup after test
            csvWriter.flush();

            runOnUiThread(() -> {
                showResult("CPU Test", totalTime);
            });
        }).start();
    }

    private CpuResult executeSingleIteration(int index) {
        long start = System.currentTimeMillis();
        CpuResult result = CPUTest.runBenchmarkIterations(Config.sampleCount, null);
        long duration = System.currentTimeMillis() - start;

        TestResult testResult = new TestResult(
                "CPU Test",
                duration,
                "threads=" + result.threads,
                true);
        logTestResult(index, testResult);
        updateProgress(index + 1);
        return result;
    }

    @Override
    @SuppressLint("SetTextI18n")
    protected String getProgressDisplayText(int currentIteration) {
        return "CPU Test: " + currentIteration + " / " + Config.sampleCount;
    }

    @Override
    protected String getTestName() {
        return "CPU Test";
    }

    private void updateStatus(String text) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(text);
            }
        });
    }

    private void showResult(String title, long time) {
        // Logic handled by BaseTestActivity or ignored for now to fix build
        Log.i(TAG, "Result: " + title + " Time: " + time);
    }
}