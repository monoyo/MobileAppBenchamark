package com.jossy.android.mobilebenchmarkappjava.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.CPUTest;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.CpuResult;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter;

import java.io.File;
import java.util.concurrent.Executors;

public class CPUTestActivity extends AppCompatActivity {

    private static final String TAG = "CPUTestActivity";
    private static final int CONSTANT_SAMPLES = 10000;

    private TextView statusText;
    private final MutableLiveData<Integer> progressLiveData = new MutableLiveData<>();

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cpu_test);

        statusText = findViewById(R.id.cpuStatus);

        // Obserwowanie zmian w LiveData i aktualizacja UI
        progressLiveData.observe(this, currentIteration -> statusText.setText("CPU Test: " + currentIteration + " / " + CONSTANT_SAMPLES));

        Log.d(TAG, "Starting CPU test activity with " + CONSTANT_SAMPLES + " samples");
        startCPUTest();
    }

    private void startCPUTest() {
        String csvPath = getIntent().getStringExtra("csv_path");

        Log.i(TAG, "Starting CPU Batch Test: samples=" + CONSTANT_SAMPLES);

        Executors.newSingleThreadExecutor().execute(() ->
                runBenchmarkTask(csvPath)
        );
    }

    private void runBenchmarkTask(String csvPath) {
        long suiteStart = System.currentTimeMillis();
        try {
            performBatchTest(csvPath, suiteStart);
            handleSuccess(System.currentTimeMillis() - suiteStart);
        } catch (Exception e) {
            handleError(e);
        }
    }

    private void performBatchTest(String csvPath, long suiteStart) throws Exception {
        try (BufferedCsvWriter writer = new BufferedCsvWriter(new File(csvPath), 1000, 64 * 1024)) {
            writer.initialize();
            for (int i = 0; i < CPUTestActivity.CONSTANT_SAMPLES; i++) {
                runSingleIteration(i, suiteStart, writer);
            }
            writer.flush();
        }
    }

    private void runSingleIteration(int index, long suiteStart, BufferedCsvWriter writer) {
        long start = System.currentTimeMillis();
        CpuResult r = CPUTest.runBenchmarkIterations(1000000L, null);
        long duration = System.currentTimeMillis() - start;

        TestResult tr = new TestResult("CPU Test", duration, "threads=" + r.threads, true);
        TestEntry entry = new TestEntry(index, tr, start, duration, System.currentTimeMillis() - suiteStart);
        writer.write(entry);

        progressLiveData.postValue(index + 1);
    }

    private void handleSuccess(long totalTime) {
        Log.i(TAG, "CPU Batch completed in " + totalTime + "ms");
        runOnUiThread(() -> {
            TestResult result = new TestResult("CPU Test", totalTime,
                    "Batch completed: " + CPUTestActivity.CONSTANT_SAMPLES + " samples", true);
            returnResult(result);
        });
    }

    private void handleError(Exception e) {
        Log.e(TAG, "Batch CPU Test failed", e);
        runOnUiThread(() -> {
            TestResult result = new TestResult("CPU Test", 0, "Error: " + e.getMessage(), false);
            returnResult(result);
        });
    }

    private void returnResult(TestResult result) {
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}