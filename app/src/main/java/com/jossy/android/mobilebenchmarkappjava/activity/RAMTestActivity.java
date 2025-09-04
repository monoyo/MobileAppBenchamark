package com.jossy.android.mobilebenchmarkappjava.activity;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;

import com.jossy.android.mobilebenchmarkappjava.BenchmarkApplication;
import com.jossy.android.mobilebenchmarkappjava.R;
import com.jossy.android.mobilebenchmarkappjava.RAMTest;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

import java.util.concurrent.Executors;

public class RAMTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ram_test);

        Log.d("RAMTestActivity", "Starting RAM test");
        startRAMTest();
    }

    private void startRAMTest() {
        Executors.newSingleThreadExecutor().execute(() -> {
            long ramStart = System.currentTimeMillis();
            RAMTest.runBenchmark();
            long ramElapsed = System.currentTimeMillis() - ramStart;
            runOnUiThread(() -> {
                TestResult result = new TestResult("RAM Test", ramElapsed, "Memory allocation test completed", true);
                Intent intent = new Intent();
                intent.putExtra(BenchmarkApplication.RESULT, result);
                setResult(RESULT_OK, intent);
                finish();
            });
        });
    }
}
