package com.jossy.android.mobilebenchmarkappjava;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class CPUTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark);

        Log.d("CPUTestActivity", "Starting CPU test");
        startCPUTest();
    }

    private void startCPUTest() {
        long cpuStart = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            new Thread(CPUTest::runBenchmark).start();
        }
        CPUTest.runBenchmark();
        long cpuElapsed = System.currentTimeMillis() - cpuStart;

        Log.i("CPUTestActivity", "CPU test time: " + cpuElapsed + "ms");
        TextView textView = new TextView(this);
        textView.setText("Test CPU ended. Time: " + cpuElapsed + " ms");
        setContentView(textView);

        setResult(RESULT_OK);
        finish();
    }
}
