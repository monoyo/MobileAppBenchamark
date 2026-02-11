package com.jossy.android.mobilebenchmarkappjava.activity;

import android.util.Log;

import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.RAMTest;
import com.jossy.android.mobilebenchmarkappjava.consts.Config;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

public class RamTest extends BaseTestActivity {

    private static final String TAG = "RamTest";

    @Override
    protected void initializeActivity() {
        setContentView(R.layout.activity_ram_test);
        statusText = findViewById(R.id.ramStatus);
    }

    @Override
    protected void executeBenchmark() throws Exception {
        initializeCsvWriter();
        int runsPerSample = calculateRunsPerSample();

        Log.i(TAG, "Starting RAM Batch: samples=" + Config.samplesAmount + ", runs/sample=" + runsPerSample);

        for (int i = 0; i < Config.samplesAmount; i++) {
            executeSingleIteration(i, runsPerSample);
        }
        csvWriter.flush();
    }

    private int calculateRunsPerSample() {
        return 50;
    }

    private void executeSingleIteration(int index, int runsPerSample) {
        long start = System.currentTimeMillis();
        RAMTest.runBenchmark(runsPerSample);
        long duration = System.currentTimeMillis() - start;

        TestResult result = new TestResult(
                "RAM Test",
                duration,
                "RAM benchmark",
                true);
        logTestResult(index, result);
        updateProgress(index + 1);
    }

    @Override
    protected String getProgressDisplayText(int currentIteration) {
        return getString(R.string.ram_test_progress, currentIteration, Config.samplesAmount);
    }

    @Override
    protected String getTestName() {
        return "RAM Test";
    }
}
