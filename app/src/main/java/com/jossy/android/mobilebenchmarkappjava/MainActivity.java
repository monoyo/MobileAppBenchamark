package com.jossy.android.mobilebenchmarkappjava;

import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private LineChart chartMain;
    private LineChart chartFactorial;
    private Button btnStart;
    private final List<BenchmarkResult> results = new ArrayList<>();

    static class BenchmarkResult {
        String label;
        long timeMillis;
        int inputSize;
        int color;

        BenchmarkResult(String label, long timeMillis, int inputSize, int color) {
            this.label = label;
            this.timeMillis = timeMillis;
            this.inputSize = inputSize;
            this.color = color;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        chartMain = findViewById(R.id.chartMain);
        chartFactorial = findViewById(R.id.chartFactorial);
        btnStart = findViewById(R.id.btnStartBenchmark);

        chartMain.setDescription(new Description());
        chartMain.getDescription().setText("Wykres dla O(n), O(n log n)");
        chartMain.setNoDataText("Brak danych do wyświetlenia");

        chartFactorial.setDescription(new Description());
        chartFactorial.getDescription().setText("Wykres dla O(n^2), O(n!)");
        chartFactorial.setNoDataText("Brak danych do wyświetlenia");

        btnStart.setOnClickListener(v -> performBenchmarks());
    }

    private void performBenchmarks() {
        results.clear();

        new Thread(() -> {
            benchmarkWithInput("O(n)", new int[]{1000, 5000, 10000, 20000, 40000}, android.graphics.Color.BLUE, size -> {
                List<Integer> list = new ArrayList<>();
                for (int i = 1; i <= size; i++) list.add(i);
                long sum = 0;
                for (int item : list) sum += item;
            });

            benchmarkWithInput("O(n log n)", new int[]{1000, 5000, 10000, 20000, 40000}, android.graphics.Color.GREEN, size -> {
                List<Integer> list = new ArrayList<>();
                Random random = new Random();
                for (int i = 0; i < size; i++) list.add(random.nextInt());
                list.sort(Integer::compareTo);
            });

            benchmarkWithInput("O(n^2)", new int[]{100, 200, 300, 400, 500}, android.graphics.Color.RED, size -> {
                int[][] matrix = new int[size][size];
                Random random = new Random();
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++)
                        matrix[i][j] = random.nextInt();
                int total = 0;
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++)
                        total += matrix[i][j];
            });

            benchmarkWithInput("O(n!)", new int[]{5, 6, 7, 8, 9}, android.graphics.Color.MAGENTA, size -> {
                generatePermutations(createList(size));
            });
        }).start();
    }

    private void benchmarkWithInput(String label, int[] inputSizes, int color, BenchmarkTask task) {
        for (int size : inputSizes) {
            long wallStart = SystemClock.elapsedRealtime();
            task.execute(size);
            long wallEnd = SystemClock.elapsedRealtime();
            long duration = wallEnd - wallStart;

            if (duration >= 0) {
                results.add(new BenchmarkResult(label, duration, size, color));
                runOnUiThread(() -> {
                    drawCharts();
                    exportToCsv();
                });
            }
        }
    }

    private interface BenchmarkTask {
        void execute(int size);
    }

    private List<Integer> createList(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= size; i++) list.add(i);
        return list;
    }

    private List<List<Integer>> generatePermutations(List<Integer> list) {
        if (list.size() <= 1) return List.of(list);
        List<List<Integer>> perms = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Integer current = list.get(i);
            List<Integer> remaining = new ArrayList<>(list);
            remaining.remove(i);
            for (List<Integer> perm : generatePermutations(remaining)) {
                List<Integer> combined = new ArrayList<>();
                combined.add(current);
                combined.addAll(perm);
                perms.add(combined);
            }
        }
        return perms;
    }

    private void drawCharts() {
        LineData mainData = new LineData();
        LineData factorialData = new LineData();

        Map<String, List<Entry>> grouped = new HashMap<>();
        Map<String, Integer> colors = new HashMap<>();

        for (BenchmarkResult result : results) {
            grouped.computeIfAbsent(result.label, k -> new ArrayList<>())
                    .add(new Entry(result.inputSize, result.timeMillis));
            colors.put(result.label, result.color);
        }

        for (Map.Entry<String, List<Entry>> entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<Entry> entries = entry.getValue();
            LineDataSet dataSet = new LineDataSet(entries, label);
            dataSet.setDrawValues(false);
            dataSet.setDrawCircles(true);
            dataSet.setDrawCircleHole(false);
            dataSet.setColor(colors.get(label));
            dataSet.setCircleColor(colors.get(label));
            dataSet.setLineWidth(2f);

            if (label.equals("O(n^2)") || label.equals("O(n!)")) {
                factorialData.addDataSet(dataSet);
            } else {
                mainData.addDataSet(dataSet);
            }
        }

        chartMain.setData(mainData);
        chartFactorial.setData(factorialData);
        configureAxis(chartMain);
        configureAxis(chartFactorial);
    }

    private void configureAxis(LineChart chart) {
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setTextSize(12f);
        chart.getXAxis().setTextColor(android.graphics.Color.BLACK);
        chart.getXAxis().setDrawAxisLine(true);
        chart.getXAxis().setDrawGridLines(true);
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                return "n = " + (int) value;
            }
        });

        chart.getAxisLeft().setTextSize(12f);
        chart.getAxisLeft().setTextColor(android.graphics.Color.BLACK);
        chart.getAxisLeft().setDrawAxisLine(true);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                return value + " ms";
            }
        });

        chart.getAxisRight().setEnabled(false);
        chart.invalidate();
    }

    private void exportToCsv() {
        try {
            File file = new File(getExternalFilesDir(null), "benchmark_results.csv");
            FileWriter writer = new FileWriter(file, false);
            writer.append("Label,Input Size,Time (ms)\n");
            for (BenchmarkResult result : results) {
                writer.append(result.label).append(",")
                        .append(String.valueOf(result.inputSize)).append(",")
                        .append(String.valueOf(result.timeMillis)).append("\n");
            }
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
