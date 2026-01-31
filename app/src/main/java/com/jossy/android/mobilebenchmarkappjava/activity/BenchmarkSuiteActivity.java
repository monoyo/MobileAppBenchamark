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
import com.jossy.android.mobilebenchmarkappjava.config.SampleConfiguration;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Aktywność orkiestrująca wykonanie suite'a testów wydajnościowych.
 * 
 * Optymalizacje dla dużych zbiorów danych (do 1M próbek):
 * 1. Buforowany zapis CSV (BufferedCsvWriter) - eliminuje wąskie gardło I/O
 * 2. Ring buffer dla podglądu w UI - ogranicza zużycie pamięci
 * 3. Asynchroniczne zapisywanie - nie blokuje pomiarów
 * 4. Checkpointy - odzyskiwanie danych przy błędach
 * 5. Konfigurowalny rozmiar próbek (100 do 1M)
 */
public class BenchmarkSuiteActivity extends AppCompatActivity {

    private static final String TAG = "BenchmarkSuiteActivity";
    private static final String BENCHMARK_TAG = "BENCHMARK"; // Tag dla synchronizacji z Pythonem
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int ALL_TESTS = 6;
    private static final int TEST_ACTIVITY_REQUEST_CODE = 456;

    // UI Components
    private TextView currentTestInfo;
    private TextView testResults;
    private ProgressBar testProgress;
    private Button startTestsButton;
    private Button exportResultsButton;

    // Configuration - Fixed to LARGE (10,000 samples)
    private SampleConfiguration selectedConfig = SampleConfiguration.LARGE;

    // Test State
    private int currentIteration = 0;
    private int currentTestIndex = 0;
    private boolean isRunning = false;
    private long testSuiteStartTime = 0;
    private long currentIterationStartTime = 0;

    // Data Storage - Optimized for large datasets
    private final Handler handler = new Handler(Looper.getMainLooper());
    private File outputDir;
    private String sessionTimestamp;

    // Statistics
    private int totalSamplesCollected = 0;
    private int errorsEncountered = 0;

    private long appStartTime = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark_suite);

        initViews();
        setupButtons();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        long launchTime = System.currentTimeMillis() - appStartTime;
        currentTestInfo.setText(String.format(Locale.US,
                "App Launched in: %dms\nReady to start tests.", launchTime));

        Log.d(TAG, "onCreate completed, UI initialized");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isRunning", isRunning);
        outState.putInt("currentTestIndex", currentTestIndex);
        outState.putInt("currentIteration", currentIteration);
        outState.putInt("totalSamplesCollected", totalSamplesCollected);
        outState.putInt("errorsEncountered", errorsEncountered);
        outState.putString("sessionTimestamp", sessionTimestamp);
        Log.i(TAG, "State saved");
    }

    private void restoreState(Bundle savedState) {
        isRunning = savedState.getBoolean("isRunning");
        currentTestIndex = savedState.getInt("currentTestIndex");
        currentIteration = savedState.getInt("currentIteration");
        totalSamplesCollected = savedState.getInt("totalSamplesCollected");
        errorsEncountered = savedState.getInt("errorsEncountered");
        sessionTimestamp = savedState.getString("sessionTimestamp");

        if (isRunning && sessionTimestamp != null) {
            try {
                restoreSession();
            } catch (IOException e) {
                Log.e(TAG, "Failed to restore session", e);
                isRunning = false;
                Toast.makeText(this, "Failed to restore session: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        updateProgressMax();
        updateProgress();
    }

    private void restoreSession() throws IOException {
        File baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        outputDir = new File(baseDir, "benchmarks/" + sessionTimestamp);

        if (!outputDir.exists()) {
            // If directory is gone, we can't really resume properly, but let's try to
            // handle gracefully
            if (!outputDir.mkdirs()) {
                throw new IOException("Cannot restore output directory: " + outputDir.getAbsolutePath());
            }
        }

        Log.i(TAG, "Session restored: " + outputDir.getAbsolutePath());
    }

    private void initViews() {
        currentTestInfo = findViewById(R.id.currentTestInfo);
        testResults = findViewById(R.id.testResults);
        testProgress = findViewById(R.id.testProgress);
        startTestsButton = findViewById(R.id.startTestsButton);
        exportResultsButton = findViewById(R.id.exportResultsButton);
    }

    private void setupButtons() {
        startTestsButton.setOnClickListener(v -> {
            if (!isRunning) {
                startTestSuite();
            }
        });

        exportResultsButton.setOnClickListener(v -> exportResults());
    }

    private void updateProgressMax() {
        testProgress.setMax(selectedConfig.sampleCount * ALL_TESTS);
    }

    private void startTestSuite() {
        if (!checkPermissions()) {
            requestPermissions();
            return;
        }

        try {
            initializeSession();
        } catch (IOException e) {
            Toast.makeText(this, "Cannot initialize output: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to initialize session", e);
            return;
        }

        isRunning = true;
        currentIteration = 0;
        currentTestIndex = 0;
        totalSamplesCollected = 0;
        errorsEncountered = 0;
        testSuiteStartTime = System.currentTimeMillis();

        testResults.setText("");
        startTestsButton.setText("Uruchamianie...");
        startTestsButton.setEnabled(false);
        updateProgressMax();

        Log.i(TAG, "Starting test suite: " + selectedConfig.displayName +
                " (" + selectedConfig.sampleCount + " samples per test)");

        runNextTest();
    }

    /**
     * Inicjalizuje sesję testową - tworzy katalog wyjściowy.
     */
    private void initializeSession() throws IOException {
        sessionTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        File baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        outputDir = new File(baseDir, "benchmarks/" + sessionTimestamp);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory: " + outputDir.getAbsolutePath());
        }

        Log.i(TAG, "Session initialized: " + outputDir.getAbsolutePath());
    }

    private void runNextTest() {
        if (currentTestIndex < ALL_TESTS) {
            String testName = getTestName(currentTestIndex);
            onTestStarted(testName);
            currentIterationStartTime = System.currentTimeMillis();
            startSpecificTest(currentTestIndex);
        } else {
            onAllTestsCompleted();
        }
    }

    private String getTestName(int index) {
        return switch (index) {
            case 0 -> "UI Stress Test"; // Changed from "UI Test"
            case 1 -> "CPU Test";
            case 2 -> "RAM Test";
            case 3 -> "Image Loading Test";
            case 4 -> "API Test";
            case 5 -> "Location Test";
            default -> "Unknown Test";
        };
    }

    private void startSpecificTest(int index) {
        if (outputDir == null) {
            Log.e(TAG, "outputDir is null in startSpecificTest. Attempting to restore or aborting.");
            // Try to recover if we have timestamp (should have been restored)
            if (sessionTimestamp != null) {
                try {
                    restoreSession();
                } catch (IOException e) {
                    Log.e(TAG, "Recovery failed", e);
                    Toast.makeText(this, "Test failed: Session lost", Toast.LENGTH_LONG).show();
                    isRunning = false;
                    startTestsButton.setEnabled(true);
                    return;
                }
            } else {
                Toast.makeText(this, "Test failed: outputDir is null", Toast.LENGTH_SHORT).show();
                isRunning = false;
                startTestsButton.setEnabled(true);
                return;
            }
        }

        Intent intent;
        switch (index) {
            case 0:
                intent = new Intent(this, GPUTestActivity.class);
                intent.putExtra("session_dir", outputDir.getAbsolutePath());
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
            default:
                intent = new Intent(this, LocationTestActivity.class);
                intent.putExtra("interval", selectedConfig.samplingIntervalMs);
                break;
        }

        // Pass common simplified args
        intent.putExtra("auto_mode", true);

        // Pass CSV path for Batch Mode tests
        // (Stress Test manages its own file, but others might use this)
        String testName = getTestName(index);
        String safeName = testName.toLowerCase(Locale.US).replace(" ", "_");
        File csvFile = new File(outputDir, safeName + ".csv");
        intent.putExtra("csv_path", csvFile.getAbsolutePath());

        startActivityForResult(intent, TEST_ACTIVITY_REQUEST_CODE);
    }

    /**
     * Obsługuje zakończenie pojedynczego testu.
     * Stosuje try-catch dla odporności na błędy.
     */
    public void onTestCompleted(TestResult result) {
        // Log completion
        long duration = System.currentTimeMillis() - currentIterationStartTime;
        Log.i(TAG, "Test " + result.getTestName() + " completed in " + duration + "ms");

        if (result.isSuccess()) {
            totalSamplesCollected += selectedConfig.sampleCount;
        }

        // Advance to next test
        currentTestIndex++;

        // Continue with next test
        handler.postDelayed(this::runNextTest, 500);
    }

    public void onAllTestsCompleted() {
        isRunning = false;
        startTestsButton.setText("Start Tests");
        startTestsButton.setEnabled(true);

        long totalTime = System.currentTimeMillis() - testSuiteStartTime;
        String summary = String.format(Locale.US,
                "All tests completed!\n" +
                        "Total samples: %d\n" +
                        "Errors: %d\n" +
                        "Total time: %.2fs\n" +
                        "Output: %s",
                totalSamplesCollected,
                errorsEncountered,
                totalTime / 1000.0,
                outputDir != null ? outputDir.getAbsolutePath() : "N/A");
        currentTestInfo.setText(summary);

        Log.i(TAG, summary.replace("\n", ", "));
    }

    public void onTestStarted(String testName) {
        Log.i(BENCHMARK_TAG, "TEST_START:" + testName);
        String info = String.format(Locale.US, "Running: %s", testName);
        currentTestInfo.setText(info);
    }

    private void updateProgress() {
        // Simplified progress: just showing which test we are on
        int progress = (currentTestIndex + 1) * selectedConfig.sampleCount;
        testProgress.setProgress(progress);
    }

    private void exportResults() {
        if (outputDir == null || !outputDir.exists()) {
            Toast.makeText(this, "No results to export yet", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = outputDir.listFiles((dir, name) -> name.endsWith(".csv"));
        int count = files != null ? files.length : 0;

        if (count > 0) {
            Toast.makeText(this,
                    String.format(Locale.US, "Results saved: %d files in %s", count, outputDir.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No CSV files found", Toast.LENGTH_SHORT).show();
        }
    }

    private Boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    startTestSuite();
                } else {
                    Toast.makeText(this, "Permissions required to run tests", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == TEST_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    TestResult result = (TestResult) data.getSerializableExtra(BenchmarkApplication.RESULT);
                    if (result != null) {
                        onTestCompleted(result);
                    } else {
                        Log.e(TAG, "No TestResult in intent");
                        errorsEncountered++;
                        handler.postDelayed(this::runNextTest, 500);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing activity result: " + e.getMessage(), e);
                    errorsEncountered++;
                    handler.postDelayed(this::runNextTest, 500);
                }
            } else {
                // Test failed - log and continue
                errorsEncountered++;
                Log.w(TAG, "Test activity returned with error or cancel");
                handler.postDelayed(this::runNextTest, 500);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
