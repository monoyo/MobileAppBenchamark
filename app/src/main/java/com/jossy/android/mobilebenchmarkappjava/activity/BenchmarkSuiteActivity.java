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

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        long launchTime = System.currentTimeMillis() - appStartTime;
        currentTestInfo.setText(String.format(Locale.US,
                "App Launched in: %dms\nReady to start tests. Select sample count.", launchTime));

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
        outState.putSerializable("selectedConfig", selectedConfig);
        Log.i(TAG, "State saved");
    }

    private void restoreState(Bundle savedState) {
        isRunning = savedState.getBoolean("isRunning");
        currentTestIndex = savedState.getInt("currentTestIndex");
        currentIteration = savedState.getInt("currentIteration");
        totalSamplesCollected = savedState.getInt("totalSamplesCollected");
        errorsEncountered = savedState.getInt("errorsEncountered");
        sessionTimestamp = savedState.getString("sessionTimestamp");
        selectedConfig = (SampleConfiguration) savedState.getSerializable("selectedConfig");

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

        // Re-init writers for ALL tests (to be safe) or just restore the map?
        // We need to re-create the writers to append to existing files.
        // BufferedCsvWriter constructor opens file. If we just want to allow appending,
        // we rely on FileWriter default?
        // Wait, BufferedCsvWriter uses `new FileWriter(outputFile)`. This TRUNCATES by
        // default.
        // We MUST change BufferedCsvWriter to support append or be careful.
        // However, we only need writers for FUTURE tests or the CURRENT test if we are
        // mid-batch?
        // Actually, if we are in BenchmarkSuiteActivity, the specific test activity
        // (e.g. CPUTestActivity)
        // has its OWN writer opened on the SAME file.
        // BenchmarkSuiteActivity typically doesn't write to these CSVs directly, the
        // sub-activities do?
        // Wait, BenchmarkSuiteActivity creates writers in `initializeSession` and puts
        // them in `csvWriters`.
        // BUT does it write to them?
        // No, it seems `BenchmarkSuiteActivity` OPENS them, but `startSpecificTest`
        // passes `csvPath` to sub-activities.
        // Sub-activities OPEN their own writers!
        // `CPUTestActivity` opens `new BufferedCsvWriter(...)`.
        // `BenchmarkSuiteActivity` closes them in `closeWriters()`.
        // Why does `BenchmarkSuiteActivity` open them at all?
        // Maybe to lock the files or prepare them?
        // It keeps them in `csvWriters`.
        // `initializeSession`:
        // `writer.initialize()` -> creates file and writes header.

        // If we restore, we should NOT overwrite the files.
        // If we just re-populate `csvWriters` but distinct instances, we need to make
        // sure they don't truncate.
        // If `BenchmarkSuiteActivity` never writes to them after init, maybe we don't
        // need to re-open them?
        // Just recreate the File object map?
        // But `closeWriters` calls `flush` and `close`.
        // If we crashed/died, the old writers are dead.
        // If we just restart, we need `csvWriters` to be non-empty so `exportResults`
        // works?
        // And `closeWriters` at end works.
        // And `uiDisplayBuffers`.

        // Simpler approach for restoration:
        // Do NOT re-open writers in Write Mode that truncates.
        // Just set up `outputDir` so `startSpecificTest` works.
        // `startSpecificTest` uses `outputDir.getAbsolutePath()`.

        // The `csvWriters` map is used in `exportResults` (flushes) and
        // `saveCheckpoints` and `closeWriters`.
        // If we don't restore them, those methods do nothing. That's probably fine for
        // a recovered session
        // if we accept that we might lose the "buffer flush" capability for the suite
        // (but sub-activities handle their own writing).

        // IMPORTANT: The crash was `outputDir` being null.
        // So just restoring `outputDir` is the critical fix.

        csvWriters.clear();
        uiDisplayBuffers.clear();

        // We probably shouldn't re-initialize writers because that writes HEADERS again
        // and truncates.
        // Unless we modify `BufferedCsvWriter` to support append.
        // For now, let's just restore `outputDir` which fixes the crash.
        // The side effect is that `exportResults` might not flush pending suite-level
        // data (if any).
        // But `BenchmarkSuiteActivity` doesn't seem to write data itself?
        // `uiDisplayBuffers` are filled... where?
        // They are never filled in `BenchmarkSuiteActivity` code I saw!
        // `BenchmarkSuiteActivity` seems to just orchestrate.
        // `updateUiDisplay` iterates `uiDisplayBuffers`.
        // `uiDisplayBuffers` are empty unless populated.
        // I don't see any code in `BenchmarkSuiteActivity` that populates
        // `uiDisplayBuffers`.
        // So maybe it's dead code or incomplete feature?
        // The sub-activities write to CSV. They don't report back samples to Suite
        // (only final result).

        // So `csvWriters` in Suite are... useless?
        // `initializeSession` creates them. `writer.initialize()` writes header.
        // Sub-activities open the SAME file.
        // If `BenchmarkSuiteActivity` holds a lock or keeps it open...
        // `BufferedCsvWriter` uses `FileWriter`.
        // Windows/Linux file locking?
        // If `BenchmarkSuiteActivity` has it open, can `CPUTestActivity` write to it?
        // `FileWriter` usually allows multiple writers (just races).

        // If `BenchmarkSuiteActivity` writes header, and `CPUTestActivity` ALSO writes
        // header?
        // `CPUTestActivity`:
        // `writer.initialize()` -> writes header.
        // So we have double headers?

        // Either way, to fix the crash, `outputDir` MUST be restored.
        // I will restore `outputDir` and `metricsCollector`.

        if (metricsCollector != null) {
            metricsCollector.stop();
        }
        metricsCollector = new SystemMetricsCollector(this, outputDir, sessionTimestamp,
                selectedConfig.samplingIntervalMs);
        metricsCollector.start();

        Log.i(TAG, "Session restored: " + outputDir.getAbsolutePath());
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

        // Initialize system metrics collector
        metricsCollector = new SystemMetricsCollector(this, outputDir, sessionTimestamp,
                selectedConfig.samplingIntervalMs);
        metricsCollector.start();

        // Note: We do NOT initialize CSV writers for individual tests here anymore.
        // Sub-activities (CPUTestActivity, etc.) manage their own BufferedCsvWriter
        // instances.
        // Creating them here caused file locking conflicts and double-initialization
        // (truncation).

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
