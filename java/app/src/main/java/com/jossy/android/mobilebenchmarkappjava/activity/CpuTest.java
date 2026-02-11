package com.jossy.android.mobilebenchmarkappjava.activity;

import android.annotation.SuppressLint;

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
    protected void executeBenchmark() throws Exception {
        initializeCsvWriter();
        for (int i = 0; i < Config.samplesAmount; i++) {
            executeSingleIteration(i);
        }
        csvWriter.flush();
    }

    private void executeSingleIteration(int index) {
        long start = System.currentTimeMillis();
        CpuResult result = CPUTest.runBenchmarkIterations(Config.samplesAmount, null);
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
        return "CPU Test: " + currentIteration + " / " + Config.samplesAmount;
    }

    @Override
    protected String getTestName() {
        return "CPU Test";
    }
}