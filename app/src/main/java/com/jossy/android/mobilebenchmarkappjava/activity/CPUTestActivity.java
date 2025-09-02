package com.jossy.android.mobilebenchmarkappjava.activity;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.jossy.android.mobilebenchmarkappjava.CPUTest;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;
import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import android.content.Intent;

import java.util.concurrent.Executors;

public class CPUTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cpu_test);

        Log.d("CPUTestActivity", "Starting CPU test");
        startCPUTest();
    }

    private void startCPUTest() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long cpuStart = System.currentTimeMillis();
            CPUTest.CpuResult r = CPUTest.runBenchmarkParallel(3000L, null);
            long cpuElapsed = System.currentTimeMillis() - cpuStart;
            Log.i("CPUTestActivity", "CPU test time: " + cpuElapsed + "ms, threads=" + r.threads + ", iters=" + r.iterations);

            runOnUiThread(() -> {
                TestResult result = new TestResult(
                        "CPU Test",
                        cpuElapsed,
                        "threads=" + r.threads + ", iterations=" + r.iterations,
                        true
                );

                Intent intent = new Intent();
                intent.putExtra(BenchmarkApplication.RESULT, result);
                setResult(RESULT_OK, intent);
                finish();
            });
        });
    }

}
