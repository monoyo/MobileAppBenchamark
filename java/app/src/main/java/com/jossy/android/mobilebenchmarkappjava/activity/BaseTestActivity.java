package com.jossy.android.mobilebenchmarkappjava.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.consts.Config;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * Base class for test activities to reduce code duplication.
 * Handles CSV writing, result logging, and progress updates.
 */
public abstract class BaseTestActivity extends AppCompatActivity {

    private static final String TAG = "BaseTestActivity";

    protected TextView statusText;
    protected final MutableLiveData<Integer> progressLiveData = new MutableLiveData<>();
    protected BufferedCsvWriter csvWriter;
    protected long suiteStartTime;
    protected String csvPath;
    protected int currentSampleIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeActivity();

        progressLiveData.observe(this, this::updateProgressText);

        csvPath = getIntent().getStringExtra("csv_path");
        Log.d(TAG, "Test activity initialized");

        startBenchmarkAsync();
    }

    /**
     * Subclasses implement this to set content view and initialize UI elements.
     */
    protected abstract void initializeActivity();

    /**
     * Subclasses implement this to perform the actual benchmark.
     */
    protected abstract void executeBenchmark() throws Exception;

    /**
     * Called when benchmark results need to be formatted for display.
     */
    protected abstract String getProgressDisplayText(int currentIteration);

    /**
     * Start benchmark execution on background thread.
     */
    protected void startBenchmarkAsync() {
        Executors.newSingleThreadExecutor().execute(this::runBenchmarkTask);
    }

    /**
     * Main benchmark task - wraps execution with error handling.
     */
    private void runBenchmarkTask() {
        suiteStartTime = System.currentTimeMillis();
        try {
            executeBenchmark();
            handleSuccess(System.currentTimeMillis() - suiteStartTime);
        } catch (Exception e) {
            handleError(e);
        }
    }

    /**
     * Log a single test iteration result to CSV.
     */
    protected void logTestResult(int iteration, TestResult result) {
        try {
            if (csvWriter == null) {
                initializeCsvWriter();
            }

            long start = System.currentTimeMillis();
            long duration = result.getExecutionTimeMs();
            long elapsed = System.currentTimeMillis() - suiteStartTime;

            TestEntry entry = new TestEntry(iteration, result, start, duration, elapsed);
            csvWriter.write(entry);
        } catch (Exception e) {
            Log.e(TAG, "Failed to log result: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize CSV writer with default settings.
     */
    protected void initializeCsvWriter() throws Exception {
        if (csvWriter != null)
            return;

        File file = new File(csvPath != null ? csvPath : getDefaultCsvPath());
        csvWriter = new BufferedCsvWriter(file, Config.bufferSize, 64 * 1024);
        csvWriter.initialize();
    }

    /**
     * Get default CSV file path if not provided.
     */
    protected String getDefaultCsvPath() {
        return getFilesDir() + "/benchmark_results.csv";
    }

    /**
     * Update progress display on UI thread.
     */
    protected void updateProgress(int currentIteration) {
        progressLiveData.postValue(currentIteration);
    }

    /**
     * Update progress text display.
     */
    private void updateProgressText(Integer currentIteration) {
        if (statusText != null) {
            statusText.setText(getProgressDisplayText(currentIteration));
        }
    }

    /**
     * Handle successful benchmark completion.
     */
    protected void handleSuccess(long totalTime) {
        Log.i(TAG, "Benchmark completed in " + totalTime + "ms");
        runOnUiThread(() -> {
            TestResult result = new TestResult(
                    getTestName(),
                    totalTime,
                    "Batch completed: " + Config.sampleCount + " samples",
                    true);
            closeCsvWriter();
            returnResult(result);
        });
    }

    /**
     * Handle benchmark error.
     */
    protected void handleError(Exception e) {
        Log.e(TAG, "Benchmark failed: " + e.getMessage(), e);
        runOnUiThread(() -> {
            TestResult result = new TestResult(
                    getTestName(),
                    0,
                    "Error: " + e.getMessage(),
                    false);
            closeCsvWriter();
            returnResult(result);
        });
    }

    /**
     * Close CSV writer safely.
     */
    protected void closeCsvWriter() {
        try {
            if (csvWriter != null) {
                csvWriter.flush();
                csvWriter.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing CSV writer: " + e.getMessage(), e);
        }
    }

    /**
     * Return test result to calling activity.
     */
    private void returnResult(TestResult result) {
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     * Get test name for logging and results.
     */
    protected abstract String getTestName();
}
