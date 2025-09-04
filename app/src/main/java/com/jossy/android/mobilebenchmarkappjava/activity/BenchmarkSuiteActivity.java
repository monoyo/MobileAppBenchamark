package com.jossy.android.mobilebenchmarkappjava.activity;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;

public class BenchmarkSuiteActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int TEST_ITERATIONS = 1;
    private static final int ALL_TESTS = 6;
    private static final int TEST_ACTIVITY_REQUEST_CODE = 456;

    private TextView currentTestInfo;
    private TextView testResults;
    private ProgressBar testProgress;
    private Button startTestsButton;
    private Button exportResultsButton;
    
    private final List<TestResult> allResults = new ArrayList<>();
    private final Map<String, List<TestEntry>> perTestResults = new LinkedHashMap<>();
    private int currentIteration = 0;
    private int currentTestIndex = 0;
    private boolean isRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private StringBuilder resultBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long testStartTime = System.currentTimeMillis();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark_suite);

        currentTestInfo = findViewById(R.id.currentTestInfo);
        testResults = findViewById(R.id.testResults);
        testProgress = findViewById(R.id.testProgress);
        startTestsButton = findViewById(R.id.startTestsButton);
        exportResultsButton = findViewById(R.id.exportResultsButton);

        startTestsButton.setOnClickListener(v -> {
            if (!isRunning) {
                startTestSuite();
            }
        });

        exportResultsButton.setOnClickListener(v -> exportResults());

        testProgress.setMax(TEST_ITERATIONS * ALL_TESTS);
        Log.d("BenchmarkSuiteActivity", "onCreate completed, UI initialized");
        long launchTime = System.currentTimeMillis() - testStartTime;
        currentTestInfo.append("App Launched in: ");
        currentTestInfo.append(Long.toString(launchTime));
        currentTestInfo.append("ms \nReady to start tests.");
    }

    private void startTestSuite() {
        if (checkPermissions()) {
            isRunning = true;
            currentIteration = 0;
            currentTestIndex = 0;
            allResults.clear();
            resultBuilder = new StringBuilder();
            testResults.setText("");
            startTestsButton.setText("Running...");
            startTestsButton.setEnabled(false);
            runNextTest();
        }
        else {
            requestPermissions();
        }
    }

    private void runNextTest() {
        if (currentTestIndex < ALL_TESTS) {
            if (currentIteration < TEST_ITERATIONS) {
                String testName = getTestName(currentTestIndex);
                onTestStarted(testName);
                startSpecificTest(currentTestIndex);
                currentIteration++;
            } else {
                currentTestIndex++;
                currentIteration = 0;
                if (currentTestIndex < ALL_TESTS) {
                    runNextTest();
                } else {
                    onAllTestsCompleted();
                }
            }
        } else {
            onAllTestsCompleted();
        }
    }

    private String getTestName(int index) {
        return switch (index) {
            case 0 -> "UI Test";
            case 1 -> "CPU Test";
            case 2 -> "RAM Test";
            case 3 -> "Image Loading Test";
            case 4 -> "API Test";
            case 5 -> "Location Test";
            default -> "Unknown Test";
        };
    }

    private void startSpecificTest(int index) {
        Intent intent;
        switch (index) {
            case 0:
                intent = new Intent(this, UITestActivity.class);
                break;
            case 1:
                intent = new Intent(this, CPUTestActivity.class);
                break;
            case 2:
                intent = new Intent(this, RAMTestActivity.class);
                break;
            case 3:
                intent = new Intent(this, ImageLoadingActivity.class);
                break;
            case 4:
                intent = new Intent(this, ApiTestActivity.class);
                break;
             case 5:
                 intent = new Intent(this, LocationTestActivity.class);
                 break;
            default:
                Log.w("BenchmarkSuiteActivity", "Invalid test index: " + index);
                return;
        }
        intent.putExtra("auto_mode", true);
        intent.putExtra("callback_activity", BenchmarkSuiteActivity.class.getName());
        startActivityForResult(intent, TEST_ACTIVITY_REQUEST_CODE);
    }

    public void onTestCompleted(TestResult result) {
        allResults.add(result);
        int iterationNum = currentIteration;
        TestEntry entry = new TestEntry(iterationNum, result);
        List<TestEntry> list = perTestResults.computeIfAbsent(result.getTestName(), k -> new ArrayList<>());
        list.add(entry);
        updateProgress();
        updateCsvDisplay();
        handler.postDelayed(this::runNextTest, 1000);
    }

    public void onAllTestsCompleted() {
        isRunning = false;
        startTestsButton.setText("Start Tests");
        startTestsButton.setEnabled(true);
        currentTestInfo.setText("All tests completed!");
        calculateAndDisplayAverages();
    }

    public void onTestStarted(String testName) {
        currentTestInfo.setText(String.format("Running: %s (Iteration %d/%d)",
                testName, currentIteration + 1, TEST_ITERATIONS));
    }

    private void updateProgress() {
    int progress = (currentTestIndex * TEST_ITERATIONS) + currentIteration;
        testProgress.setProgress(progress);
    }

    private void updateCsvDisplay() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<TestEntry>> e : perTestResults.entrySet()) {
            String testName = e.getKey();
            List<TestEntry> entries = e.getValue();
            sb.append("# ").append(testName).append('\n');
            sb.append("iteration,executionTimeMs,details\n");
            List<TestEntry> sorted = new ArrayList<>(entries);
            sorted.sort(Comparator.comparingInt(a -> a.iteration));
            for (TestEntry te : sorted) {
                sb.append(te.iteration).append(',')
                  .append(te.result.getExecutionTime()).append(',')
                  .append(csv(te.result.getDetails())).append(',')
                  .append('\n');
            }
            sb.append('\n');
        }
        testResults.setText(sb.toString());
        resultBuilder = sb;
    }

    private void calculateAndDisplayAverages() {
        StringBuilder averages = new StringBuilder("\nAverages:\n");
        for (Map.Entry<String, List<TestEntry>> e : perTestResults.entrySet()) {
            List<TestEntry> entries = e.getValue();
            long sum = 0L;
            int count = 0;
            for (TestEntry te : entries) {
                sum += te.result.getExecutionTime();
                count++;
            }
            if (count > 0) {
                double avg = (double) sum / (double) count;
                averages.append(String.format(Locale.US, "%s: %.2fms\n", e.getKey(), avg));
            }
        }
        resultBuilder.append(averages);
        testResults.setText(resultBuilder.toString());
    }

    private void exportResults() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File outDir = new File(baseDir, "benchmarks");
            if (!outDir.exists() && !outDir.mkdirs()) {
                Toast.makeText(this, "Cannot access output directory", Toast.LENGTH_SHORT).show();
                return;
            }

            int files = 0;
            for (Map.Entry<String, List<TestEntry>> e : perTestResults.entrySet()) {
                List<TestEntry> entries = e.getValue();
                if (entries.isEmpty()) continue;
                String safe = e.getKey().toLowerCase(Locale.US).replace(" ", "_");
                File file = new File(outDir, safe + "_" + timestamp + ".csv");
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(buildCsv(entries));
                }
                files++;
            }
            if (files > 0) {
                Toast.makeText(this, "Saved " + files + " CSV files to " + outDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "No results to export yet", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException ex) {
            Toast.makeText(this, "Export error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String buildCsv(List<TestEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("iteration,executionTimeMs,details\n");
        List<TestEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(a -> a.iteration));
        for (TestEntry te : sorted) {
            sb.append(te.iteration).append(',')
              .append(te.result.getExecutionTime()).append(',')
              .append(csv(te.result.getDetails())).append(',')
              .append('\n');
        }
        return sb.toString();
    }

    private String csv(String value) {
        if (value == null) return "";
        boolean needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? ("\"" + escaped + "\"") : escaped;
    }

    private Boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startTestSuite();
                } else {
                    Toast.makeText(
                            this,
                            "Permissions requires to run tests",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        } else if (requestCode == TEST_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            TestResult result = (TestResult) data.getSerializableExtra(BenchmarkApplication.RESULT);
            if (result != null) {
                onTestCompleted(result);
            } else {
                Log.e("BenchmarkSuiteActivity", "No TestResult received from test activity");
            }
        }
    }
}
