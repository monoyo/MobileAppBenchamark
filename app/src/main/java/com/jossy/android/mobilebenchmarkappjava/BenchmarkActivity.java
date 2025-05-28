package com.jossy.android.mobilebenchmarkappjava;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class BenchmarkActivity extends AppCompatActivity {
    private final ArrayList<Long> cpuTimes = new ArrayList<>();
    private final ArrayList<Long> ramTimes = new ArrayList<>();
    private final ArrayList<Long[]> ramUsages = new ArrayList<>();
    private final ArrayList<Long> uiLatencies = new ArrayList<>();
    private final ArrayList<Integer> cpuResults = new ArrayList<>();
    private int runs;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textView = new TextView(this);
        setContentView(textView);
        textView.setText("Benchmark Status: Running...");
        runs = getIntent().getIntExtra("runs", 50);
        runBenchmarks(0);
    }

    private void runSingleBenchmark(final int runIdx, final Runnable onFinish) {
        long cpuStart = System.nanoTime();
        int cpuResult = countPrimes(100_000);
        long cpuTime = (System.nanoTime() - cpuStart) / 1_000_000;
        cpuTimes.add(cpuTime);
        cpuResults.add(cpuResult);

        long ramStart = System.nanoTime();
        long ramUsageBefore = getUsedMemoryMB();
        int[] bigArray = new int[500_000];
        for (int i = 0; i < bigArray.length; i++) bigArray[i] = i;
        long ramUsageAfter = getUsedMemoryMB();
        long ramTime = (System.nanoTime() - ramStart) / 1_000_000;
        ramTimes.add(ramTime);
        ramUsages.add(new Long[]{ramUsageBefore, ramUsageAfter});
        for (int i = 0; i < bigArray.length; i++) bigArray[i] = 0;
        System.gc();

        final long uiStart = System.nanoTime();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LinearLayout tempLayout = new LinearLayout(BenchmarkActivity.this);
                tempLayout.setOrientation(LinearLayout.VERTICAL);
                for (int i = 1; i <= 200; i++) {
                    TextView tv = new TextView(BenchmarkActivity.this);
                    tv.setText("Test " + i);
                    tempLayout.addView(tv);
                }
                long uiEnd = System.nanoTime();
                long uiLatency = (uiEnd - uiStart) / 1_000_000;
                uiLatencies.add(uiLatency);
                onFinish.run();
            }
        });
    }

    private void runBenchmarks(final int current) {
        if (current >= runs) {
            double cpuAvg = average(cpuTimes);
            double ramAvg = average(ramTimes);
            double uiAvg = average(uiLatencies);
            double ramBeforeAvg = average(ramUsages, 0);
            double ramAfterAvg = average(ramUsages, 1);
            Intent resultIntent = new Intent();
            resultIntent.putExtra("cpuAvg", cpuAvg);
            resultIntent.putExtra("ramAvg", ramAvg);
            resultIntent.putExtra("uiAvg", uiAvg);
            resultIntent.putExtra("ramBeforeAvg", ramBeforeAvg);
            resultIntent.putExtra("ramAfterAvg", ramAfterAvg);
            resultIntent.putExtra("runs", runs);
            setResult(Activity.RESULT_OK, resultIntent);
            textView.setText(
                    "Benchmark Status: Done\n" +
                            String.format("CPU avg: %.2f ms\n", cpuAvg) +
                            String.format("RAM avg: %.2f ms, Used: %.2f -> %.2f MB\n", ramAvg, ramBeforeAvg, ramAfterAvg) +
                            String.format("UI Latency avg: %.2f ms\n", uiAvg) +
                            "Runs: " + runs + "\n\nZamknij okno, aby wrócić."
            );
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, 2000);
            return;
        }
        runSingleBenchmark(current, new Runnable() {
            @Override
            public void run() {
                runBenchmarks(current + 1);
            }
        });
    }

    private int countPrimes(int limit) {
        int count = 0;
        for (int i = 2; i <= limit; i++) {
            if (isPrime(i)) count++;
        }
        return count;
    }

    private boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; i <= Math.sqrt(n); i++) {
            if (n % i == 0) return false;
        }
        return true;
    }

    private long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private double average(ArrayList<Long> list) {
        if (list.isEmpty()) return 0.0;
        long sum = 0;
        for (Long l : list) sum += l;
        return sum / (double) list.size();
    }

    private double average(ArrayList<Long[]> list, int idx) {
        if (list.isEmpty()) return 0.0;
        long sum = 0;
        for (Long[] arr : list) sum += arr[idx];
        return sum / (double) list.size();
    }
}
