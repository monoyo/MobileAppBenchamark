package com.jossy.android.mobilebenchmarkappjava;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BenchmarkSuiteActivity extends AppCompatActivity implements TestCallback {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int TEST_ITERATIONS = 3;
    private static final int TEST_ACTIVITY_REQUEST_CODE = 456;

    private TextView currentTestInfo;
    private TextView testResults;
    private ProgressBar testProgress;
    private Button startTestsButton;
    private Button exportResultsButton;
    
    private final List<TestResult> allResults = new ArrayList<>();
    private int currentIteration = 0;
    private int currentTestIndex = 0;
    private boolean isRunning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private StringBuilder resultBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        testProgress.setMax(TEST_ITERATIONS * 4); // 4 tests total
        Log.d("BenchmarkSuiteActivity", "onCreate completed, UI initialized");
    }

    private void startTestSuite() {
        if (checkPermissions()) {
            Log.d("BenchmarkSuiteActivity", "Starting test suite");
            isRunning = true;
            currentIteration = 0;
            currentTestIndex = 0;
            allResults.clear();
            resultBuilder = new StringBuilder();
            testResults.setText("");
            startTestsButton.setText("Running...");
            startTestsButton.setEnabled(false);
            runNextTest();
        } else {
            Log.d("BenchmarkSuiteActivity", "Requesting permissions");
            requestPermissions();
        }
    }

    private void runNextTest() {
        if (currentIteration < TEST_ITERATIONS) {
            if (currentTestIndex < 4) { // 4 tests total
                String testName = getTestName(currentTestIndex);
                onTestStarted(testName);
                startSpecificTest(currentTestIndex);
                currentTestIndex++; // Increment index after scheduling the test
            } else {
                currentTestIndex = 0;
                currentIteration++;
                if (currentIteration < TEST_ITERATIONS) {
                    runNextTest();
                }
            }
        } else {
            onAllTestsCompleted();
        }
    }

    private String getTestName(int index) {
        Log.d("BenchmarkSuiteActivity", "Getting test name for index: " + index);
        switch (index) {
            case 0: return "CPU Test";
            case 1: return "RAM Test";
            case 2: return "Image Loading Test";
            case 3: return "API Test";
            default: return "Unknown Test";
        }
    }

    private void startSpecificTest(int index) {
        Log.d("BenchmarkSuiteActivity", "Starting specific test for index: " + index);
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
                intent = new Intent(this, ApiTestActivity.class);
                break;
            default:
                Log.w("BenchmarkSuiteActivity", "Invalid test index: " + index);
                return;
        }
        intent.putExtra("auto_mode", true);
        intent.putExtra("callback_activity", BenchmarkSuiteActivity.class.getName());
        Log.d("BenchmarkSuiteActivity", "Starting activity: " + intent.getComponent());
        startActivityForResult(intent, TEST_ACTIVITY_REQUEST_CODE);
    }

    @Override
    public void onTestCompleted(TestResult result) {
        Log.d("BenchmarkSuiteActivity", "Test completed: " + result);
        allResults.add(result);
        updateProgress();
        appendResult(result);
        currentTestIndex++;
        Log.d("BenchmarkSuiteActivity", "Scheduling next test with delay");
        handler.postDelayed(this::runNextTest, 1000); // Small delay between tests
    }

    @Override
    public void onAllTestsCompleted() {
        isRunning = false;
        startTestsButton.setText("Start Tests");
        startTestsButton.setEnabled(true);
        currentTestInfo.setText("All tests completed!");
        calculateAndDisplayAverages();
    }

    @Override
    public void onTestStarted(String testName) {
        currentTestInfo.setText(String.format("Running: %s (Iteration %d/%d)",
                testName, currentIteration + 1, TEST_ITERATIONS));
    }

    @Override
    public void onError(String testName, String error) {
        String errorMsg = String.format("Error in %s: %s\n", testName, error);
        resultBuilder.append(errorMsg);
        testResults.setText(resultBuilder.toString());
        currentTestIndex++;
        runNextTest();
    }

    private void updateProgress() {
        int progress = (currentIteration * 4) + currentTestIndex;
        testProgress.setProgress(progress);
    }

    private void appendResult(TestResult result) {
        String resultText = String.format(Locale.US,
                "[Iteration %d] %s: %dms - %s\n",
                currentIteration + 1,
                result.getTestName(),
                result.getExecutionTime(),
                result.getDetails());
        resultBuilder.append(resultText);
        testResults.setText(resultBuilder.toString());
    }

    private void calculateAndDisplayAverages() {
        StringBuilder averages = new StringBuilder("\nAverage Results:\n");
        for (int i = 0; i < 4; i++) {
            String testName = getTestName(i);
            long sum = 0;
            int count = 0;
            for (TestResult result : allResults) {
                if (result.getTestName().equals(testName)) {
                    sum += result.getExecutionTime();
                    count++;
                }
            }
            if (count > 0) {
                double avg = (double) sum / count;
                averages.append(String.format(Locale.US,
                        "%s Average: %.2fms\n", testName, avg));
            }
        }
        resultBuilder.append(averages);
        testResults.setText(resultBuilder.toString());
    }

    private void exportResults() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            File file = new File(getExternalFilesDir(null),
                    "benchmark_results_" + timestamp + ".txt");
            FileWriter writer = new FileWriter(file);
            writer.write(resultBuilder.toString());
            writer.close();
            Toast.makeText(this, "Results exported to " + file.getPath(),
                    Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error exporting results: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11 and above
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && Environment.isExternalStorageManager();
        } else {
            // For Android 10 and below
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11 and above
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            }
        } else {
            // For Android 10 and below
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startTestSuite();
                } else {
                    Toast.makeText(this, "Permissions required to run tests", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == TEST_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d("BenchmarkSuiteActivity", "Test completed, launching next test");
            handler.postDelayed(this::runNextTest, 1000); // Small delay before starting the next test
        }
    }
}
