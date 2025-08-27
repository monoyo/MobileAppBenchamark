package com.jossy.android.mobilebenchmarkappjava;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import com.jossy.android.mobilebenchmarkappjava.data.TestResult;

public class RAMTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benchmark);

        Log.d("RAMTestActivity", "Starting RAM test");
        startRAMTest();
    }

    private void startRAMTest() {
        long ramStart = System.currentTimeMillis();
        RAMTest.runBenchmark();
        long ramElapsed = System.currentTimeMillis() - ramStart;

        Log.i("RAMTestActivity", "RAM test time: " + ramElapsed + "ms");
        TextView textView = new TextView(this);
        textView.setText("Test RAM ended. Time: " + ramElapsed + " ms");
        setContentView(textView);

        TestResult result = new TestResult("RAM Test", ramElapsed, "Memory allocation test completed", true);
        Intent intent = new Intent();
        intent.putExtra(BenchmarkApplication.RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }
}
