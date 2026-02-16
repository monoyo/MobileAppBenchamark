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
    protected void executeBenchmark() throws Exception {
        CPUTest.initialize(null);
        initializeCsvWriter();

        for (int i = 0; i < Config.sampleCount; i++) {
            executeSingleIteration(i);
        }

        if (csvWriter != null)
            csvWriter.flush();
        CPUTest.shutdown();
    }

    private void executeSingleIteration(int index) {
        long start = System.currentTimeMillis();
        CpuResult result = CPUTest.runBenchmarkIterations(Config.cpuIterations, null);
        long duration = System.currentTimeMillis() - start;

        TestResult testResult = new TestResult(
                "CPU Test",
                duration,
                "threads=" + result.threads,
                true);
        logTestResult(index, testResult);
        updateProgress(index + 1);
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

    @Override
    protected void initializeCsvWriter() throws Exception {
        if (csvWriter != null)
            return;

        java.io.File file = new java.io.File(csvPath != null ? csvPath : getDefaultCsvPath());
        csvWriter = new com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter(file, Config.bufferSize,
                64 * 1024, "CPU Test",
                new com.jossy.android.mobilebenchmarkappjava.io.BufferedCsvWriter.CsvFormatter() {
                    @Override
                    public String format(com.jossy.android.mobilebenchmarkappjava.data.TestEntry entry) {
                        return entry.iteration + "," + entry.result.getExecutionTimeMs() + "\n";
                    }

                    @Override
                    public String getHeader() {
                        return "iteration,elapsedTimeMs\n";
                    }
                });
        csvWriter.initialize();
    }
}