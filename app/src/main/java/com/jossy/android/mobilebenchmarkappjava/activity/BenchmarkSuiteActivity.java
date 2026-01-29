package com.jossy.android.mobilebenchmarkappjava.activity;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.config.SampleConfiguration;
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter;
import com.jossy.android.mobilebenchmarkappjava.metrics.SystemMetricsCollector;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
    private static final int UI_DISPLAY_BUFFER_SIZE = 100; // Max entries shown in UI

    // UI Components
    private TextView currentTestInfo;
    private TextView testResults;
    private ProgressBar testProgress;
    private Button startTestsButton;
    private Button exportResultsButton;
    private Spinner sampleConfigSpinner;

    // Configuration
    private SampleConfiguration selectedConfig = SampleConfiguration.SMALL;

    // Test State
    private int currentIteration = 0;
    private int currentTestIndex = 0;
    private boolean isRunning = false;
    private long testSuiteStartTime = 0;
    private long currentIterationStartTime = 0;

    // Data Storage - Optimized for large datasets
    private final Map<String, BufferedCsvWriter> csvWriters = new LinkedHashMap<>();
    private final Map<String, Deque<TestEntry>> uiDisplayBuffers = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private File outputDir;
    private String sessionTimestamp;

    // Statistics
    private int totalSamplesCollected = 0;
    private int errorsEncountered = 0;

    // System Metrics Collector - zbiera CPU, RAM, GPU, FPS do osobnego CSV
    private SystemMetricsCollector metricsCollector;

    private long appStartTime = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark_suite);

        initViews();
        setupSampleConfigSpinner();
        setupButtons();

        long launchTime = System.currentTimeMillis() - appStartTime;
        currentTestInfo.setText(String.format(Locale.US,
                "App Launched in: %dms\nReady to start tests. Select sample count.", launchTime));

        Log.d(TAG, "onCreate completed, UI initialized");
    }

    private void initViews() {
        currentTestInfo = findViewById(R.id.currentTestInfo);
        testResults = findViewById(R.id.testResults);
        testProgress = findViewById(R.id.testProgress);
        startTestsButton = findViewById(R.id.startTestsButton);
        exportResultsButton = findViewById(R.id.exportResultsButton);
        sampleConfigSpinner = findViewById(R.id.sampleConfigSpinner);
    }

    private void setupSampleConfigSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SampleConfiguration.getDisplayNames());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampleConfigSpinner.setAdapter(adapter);

        sampleConfigSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedConfig = SampleConfiguration.fromIndex(position);
                updateProgressMax();
                Log.i(TAG, "Selected configuration: " + selectedConfig.displayName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedConfig = SampleConfiguration.SMALL;
            }
        });
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
        sampleConfigSpinner.setEnabled(false);
        updateProgressMax();

        Log.i(TAG, "Starting test suite: " + selectedConfig.displayName +
                " (" + selectedConfig.sampleCount + " samples per test)");

        runNextTest();
    }

    /**
     * Inicjalizuje sesję testową - tworzy katalog wyjściowy i writery CSV.
     */
    private void initializeSession() throws IOException {
        sessionTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        File baseDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        outputDir = new File(baseDir, "benchmarks/" + sessionTimestamp);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory: " + outputDir.getAbsolutePath());
        }

        // Zamknij poprzednie writery jeśli istnieją
        closeWriters();
        csvWriters.clear();
        uiDisplayBuffers.clear();

        // Uruchom kolektor metryk systemowych (CPU, RAM, GPU, FPS)
        if (metricsCollector != null) {
            metricsCollector.stop();
        }
        metricsCollector = new SystemMetricsCollector(this, outputDir, sessionTimestamp);
        metricsCollector.start();

        // Inicjalizuj writery dla każdego testu
        for (int i = 0; i < ALL_TESTS; i++) {
            String testName = getTestName(i);
            String safeName = testName.toLowerCase(Locale.US).replace(" ", "_");
            File csvFile = new File(outputDir, safeName + ".csv");

            BufferedCsvWriter writer = new BufferedCsvWriter(
                    csvFile,
                    selectedConfig.bufferSize,
                    selectedConfig.getOptimalWriteBufferBytes());
            writer.initialize();
            csvWriters.put(testName, writer);

            // Ring buffer dla UI display
            uiDisplayBuffers.put(testName, new ArrayDeque<>(UI_DISPLAY_BUFFER_SIZE));
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
        Intent intent;
        switch (index) {
            case 0:
                intent = new Intent(this, StressTestActivity.class);
                intent.putExtra("session_dir", outputDir.getAbsolutePath());
                intent.putExtra("iterations", selectedConfig.sampleCount);
                break;
            case 1:
                intent = new Intent(this, CPUTestActivity.class);
                intent.putExtra("iterations", selectedConfig.sampleCount); // Pass total sample count
                intent.putExtra("cpu_iterations", selectedConfig.getCpuIterationsPerThread());
                break;
            case 2:
                intent = new Intent(this, RAMTestActivity.class);
                intent.putExtra("iterations", selectedConfig.sampleCount);
                break;
            case 3:
                intent = new Intent(this, ImageLoadingActivity.class);
                intent.putExtra("iterations", selectedConfig.sampleCount);
                break;
            case 4:
                intent = new Intent(this, ApiTestActivity.class);
                intent.putExtra("iterations", selectedConfig.sampleCount);
                break;
            case 5:
                intent = new Intent(this, LocationTestActivity.class);
                intent.putExtra("iterations", selectedConfig.sampleCount);
                break;
            default:
                Log.w(TAG, "Invalid test index: " + index);
                return;
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
        sampleConfigSpinner.setEnabled(true);

        // Zamknij wszystkie writery (flush pozostałych danych)
        closeWriters();

        // Zatrzymaj kolektor metryk systemowych
        if (metricsCollector != null) {
            metricsCollector.stop();
        }

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

        // Wyświetl statystyki końcowe
        calculateAndDisplayAverages();
    }

    public void onTestStarted(String testName) {
        Log.i(BENCHMARK_TAG, "TEST_START:" + testName);
        String info = String.format(Locale.US, "Running: %s\nConfiguration: %s",
                testName, selectedConfig.displayName);
        currentTestInfo.setText(info);
    }

    private void updateProgress() {
        // Simplified progress: just showing which test we are on
        int progress = (currentTestIndex + 1) * selectedConfig.sampleCount;
        // Note: Progress bar logic in layout might need adjustment or we just max it
        // out per test
        testProgress.setProgress(progress);
    }

    /**
     * Aktualizuje wyświetlanie wyników w UI.
     * Pokazuje tylko ostatnie N rekordów z ring buffera (oszczędza pamięć).
     */
    private void updateUiDisplay() {
        // Aktualizuj UI co N iteracji aby nie spowalniać przy dużych próbkach
        if (currentIteration % Math.max(1, selectedConfig.sampleCount / 100) != 0 &&
                currentIteration != selectedConfig.sampleCount - 1) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Live Preview (last ").append(UI_DISPLAY_BUFFER_SIZE).append(" per test) ===\n\n");

        for (String testName : uiDisplayBuffers.keySet()) {
            sb.append("# ").append(testName).append(" (Batch Mode Running...)\n\n");
        }

        testResults.setText(sb.toString());
    }

    private void calculateAndDisplayAverages() {
        StringBuilder averages = new StringBuilder("\n=== Averages ===\n");

        for (Map.Entry<String, Deque<TestEntry>> e : uiDisplayBuffers.entrySet()) {
            Deque<TestEntry> entries = e.getValue();
            if (entries.isEmpty())
                continue;

            long sum = 0L;
            for (TestEntry te : entries) {
                sum += te.result.getExecutionTime();
            }
            double avg = (double) sum / entries.size();
            averages.append(String.format(Locale.US, "%s: %.2fms (based on %d recent samples)\n",
                    e.getKey(), avg, entries.size()));
        }

        testResults.append(averages.toString());
    }

    private void exportResults() {
        if (outputDir == null || !outputDir.exists()) {
            Toast.makeText(this, "No results to export yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Flush wszystkie writery
        for (BufferedCsvWriter writer : csvWriters.values()) {
            writer.flush();
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

    /**
     * Zapisuje checkpointy wszystkich writerów na wypadek błędu.
     */
    private void saveAllCheckpoints() {
        for (BufferedCsvWriter writer : csvWriters.values()) {
            writer.saveCheckpoint();
        }
        Log.i(TAG, "All checkpoints saved");
    }

    /**
     * Zamyka wszystkie writery CSV.
     */
    private void closeWriters() {
        for (BufferedCsvWriter writer : csvWriters.values()) {
            try {
                writer.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing writer: " + e.getMessage(), e);
            }
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
                    saveAllCheckpoints();
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
        // Upewnij się, że writery są zamknięte
        closeWriters();
    }
}
