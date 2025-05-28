package com.jossy.android.mobilebenchmarkappjava;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private long startTime;
    private ActivityResultLauncher<Intent> benchmarkLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startTime = System.currentTimeMillis();

        TextView benchmarkStatus = findViewById(R.id.benchmarkStatus);
        EditText editRuns = findViewById(R.id.editRuns);

        benchmarkLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            double cpuAvg = data.getDoubleExtra("cpuAvg", 0.0);
                            double ramAvg = data.getDoubleExtra("ramAvg", 0.0);
                            double uiAvg = data.getDoubleExtra("uiAvg", 0.0);
                            double ramBeforeAvg = data.getDoubleExtra("ramBeforeAvg", 0.0);
                            double ramAfterAvg = data.getDoubleExtra("ramAfterAvg", 0.0);
                            int runs = data.getIntExtra("runs", 0);
                            String logMsg =
                                    "Benchmark Status: Done\n" +
                                            String.format("CPU avg: %.2f ms\n", cpuAvg) +
                                            String.format("RAM avg: %.2f ms, Used: %.2f -> %.2f MB\n", ramAvg, ramBeforeAvg, ramAfterAvg) +
                                            String.format("UI Latency avg: %.2f ms\n", uiAvg) +
                                            "Runs: " + runs;
                            benchmarkStatus.setText(logMsg);
                            Log.i("BENCHMARK_RESULT", logMsg);
                        }
                    }
                }
        );

        editRuns.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                String text = editRuns.getText().toString();
                // Usuwanie znaków innych niż cyfry
                StringBuilder filtered = new StringBuilder();
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (Character.isDigit(c)) {
                        filtered.append(c);
                    }
                }
                String filteredStr = filtered.toString();
                if (!text.equals(filteredStr)) {
                    editRuns.setText(filteredStr);
                    editRuns.setSelection(filteredStr.length());
                }
                return false;
            }
        });

        findViewById(R.id.btnStartBenchmark).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String runsText = editRuns.getText().toString();
                int runs;
                try {
                    runs = Integer.parseInt(runsText);
                } catch (NumberFormatException e) {
                    runs = 50;
                }
                if (runs > 80) runs = 70;
                if (runs < 1) runs = 1;
                Intent intent = new Intent(MainActivity.this, BenchmarkActivity.class);
                intent.putExtra("runs", runs);
                benchmarkLauncher.launch(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reportFullyDrawn();
        long elapsedTime = System.currentTimeMillis() - startTime;
        TextView launchTime = findViewById(R.id.appLaunchTime);
        launchTime.setText("App Launch Time: " + elapsedTime + "ms");
    }
}
