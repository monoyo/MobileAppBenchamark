package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.RAMTest;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter;

import java.io.File;
import java.util.concurrent.Executors;

public class RAMTestActivity extends AppCompatActivity {
    private static final int TARGET_SAMPLES = 10000;

    private static final String TAG = "RAMTestActivity";
    private TextView statusText;
    private final MutableLiveData<Integer> progressLiveData = new MutableLiveData<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ram_test);

        statusText = findViewById(R.id.cpuStatus);

        // Obserwowanie zmian w LiveData i aktualizacja UI (podobnie jak w CPUTestActivity)
        progressLiveData.observe(this, currentIteration -> 
                statusText.setText("RAM Test: " + currentIteration + " / " + TARGET_SAMPLES));

        Log.d(TAG, "Starting RAM test activity");
        startRAMTest();
    }

    private void startRAMTest() {
        String csvPath = getIntent().getStringExtra("csv_path");
        int runsPerSample = calculateRunsPerSample(TARGET_SAMPLES);

        Log.i(TAG, "Starting RAM Batch: samples=" + TARGET_SAMPLES + ", runs/sample=" + runsPerSample);
        statusText.setText("Initializing RAM Batch...");

        Executors.newSingleThreadExecutor().execute(() ->
                performBenchmarkTask(TARGET_SAMPLES, csvPath, runsPerSample)
        );
    }

    private int calculateRunsPerSample(int targetSamples) {
        return targetSamples > 1000 ? 50 : 500;
    }

    private void performBenchmarkTask(int targetSamples, String csvPath, int runsPerSample) {
        long suiteStart = System.currentTimeMillis();
        File logFile = new File(csvPath != null ? csvPath : getFilesDir() + "/temp_ram.csv");

        try (BufferedCsvWriter writer = new BufferedCsvWriter(logFile, 1000, 64 * 1024)) {
            writer.initialize();

            runBenchmarkLoop(writer, targetSamples, runsPerSample, suiteStart);

            writer.flush();
            handleBenchmarkSuccess(suiteStart, targetSamples);

        } catch (Exception e) {
            handleBenchmarkError(e);
        }
    }

    private void runBenchmarkLoop(BufferedCsvWriter writer, int targetSamples, int runsPerSample, long suiteStart) throws Exception {
        for (int i = 0; i < targetSamples; i++) {
            long start = System.currentTimeMillis();
            RAMTest.runBenchmark(runsPerSample);
            long duration = System.currentTimeMillis() - start;

            TestResult tr = new TestResult("RAM Test", duration, "RAM benchmark", true);
            TestEntry entry = new TestEntry(i, tr, start, duration, System.currentTimeMillis() - suiteStart);
            writer.write(entry);

            // Aktualizacja postępu przez LiveData
            progressLiveData.postValue(i + 1);
        }
    }

    private void handleBenchmarkSuccess(long suiteStartTime, int targetSamples) {
        long totalTime = System.currentTimeMillis() - suiteStartTime;
        Log.i(TAG, "RAM Batch completed in " + totalTime + "ms");

        runOnUiThread(() -> {
            TestResult result = new TestResult(
                    "RAM Test", totalTime, "Batch completed: " + targetSamples + " samples", true);
            returnResult(result);
        });
    }

    private void handleBenchmarkError(Exception e) {
        Log.e(TAG, "Batch RAM Test failed", e);
        runOnUiThread(() -> {
            statusText.setText("Error: " + e.getMessage());
            TestResult result = new TestResult(
                    "RAM Test", 0, "Error: " + e.getMessage(), false);
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
