package com.jossy.android.mobilebenchmarkappjava.metrics;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Choreographer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kolektor metryk systemowych - zbiera CPU, RAM, GPU, FPS bezpośrednio z
 * aplikacji.
 * Zapisuje dane do CSV w czasie rzeczywistym.
 */
public class SystemMetricsCollector {

    private static final String TAG = "SystemMetricsCollector";

    // Konfiguracja
    private final int samplingIntervalMs;
    private static final int BUFFER_SIZE = 50; // Flush co 50 próbek

    private final Context context;
    private final File outputFile;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // Stan
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String currentTestName = "IDLE";
    private int currentIteration = 0;
    private long startTimeMs = 0;

    // Aktualne wartości dla UI
    private volatile float lastCpuUsage = 0f;
    private volatile float lastMemoryUsage = 0f;
    private volatile float currentFps = 0f;

    // Metryki CPU
    private long prevCpuTime = 0;
    private long prevAppCpuTime = 0;

    // Metryki FPS
    private final AtomicInteger frameCount = new AtomicInteger(0);
    private final AtomicLong lastFpsCalcTime = new AtomicLong(0);

    // Bufor i writer
    private BufferedWriter writer;
    private final List<MetricSample> buffer = new ArrayList<>(BUFFER_SIZE);

    // Choreographer callback dla FPS
    private Choreographer.FrameCallback frameCallback;

    public SystemMetricsCollector(Context context, File outputDir, String sessionId, int samplingIntervalMs) {
        this.context = context.getApplicationContext();
        this.outputFile = new File(outputDir, "system_metrics_" + sessionId + ".csv");
        this.samplingIntervalMs = samplingIntervalMs;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "metrics-collector");
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return;
        }

        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        writer = new BufferedWriter(new FileWriter(outputFile), 32 * 1024);
        writer.write("TimestampMs,ElapsedMs,TestName,Iteration,CPU%,RAM_MB,GPU%,FPS,PID\n");

        startTimeMs = System.currentTimeMillis();
        lastFpsCalcTime.set(startTimeMs);

        startFpsCounter();
        executor.submit(this::samplingLoop);

        Log.i(TAG, "Started metrics collection: " + outputFile.getAbsolutePath());
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        stopFpsCounter();
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

        try {
            synchronized (buffer) {
                flushBuffer();
            }
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing writer", e);
        }

        Log.i(TAG, "Stopped metrics collection");
    }

    public void setCurrentTest(String testName, int iteration) {
        this.currentTestName = testName;
        this.currentIteration = iteration;
    }

    public float getLastCpuUsage() {
        return lastCpuUsage;
    }

    public float getLastMemoryUsage() {
        return lastMemoryUsage;
    }

    public float getLastFps() {
        return currentFps;
    }

    private void samplingLoop() {
        while (running.get()) {
            long loopStart = System.currentTimeMillis();

            try {
                MetricSample sample = collectSample();
                // Aktualizuj wartości dla UI
                lastCpuUsage = sample.cpuPercent;
                lastMemoryUsage = sample.ramMb;

                synchronized (buffer) {
                    buffer.add(sample);
                    if (buffer.size() >= BUFFER_SIZE) {
                        flushBuffer();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Sampling error", e);
            }

            long elapsed = System.currentTimeMillis() - loopStart;
            long sleepMs = Math.max(1, samplingIntervalMs - elapsed);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    private MetricSample collectSample() {
        long now = System.currentTimeMillis();
        return new MetricSample(
                now,
                now - startTimeMs,
                currentTestName,
                currentIteration,
                getCpuUsage(),
                getMemoryUsageMB(),
                getGpuUsage(),
                currentFps,
                Process.myPid());
    }

    private float getCpuUsage() {
        try {
            int pid = Process.myPid();
            RandomAccessFile procStat = new RandomAccessFile("/proc/" + pid + "/stat", "r");
            String statLine = procStat.readLine();
            procStat.close();

            String[] parts = statLine.split("\\s+");
            if (parts.length < 15) return 0f;

            long utime = Long.parseLong(parts[13]);
            long stime = Long.parseLong(parts[14]);
            long appCpuTime = utime + stime;

            RandomAccessFile cpuStat = new RandomAccessFile("/proc/stat", "r");
            String cpuLine = cpuStat.readLine();
            cpuStat.close();

            String[] cpuParts = cpuLine.split("\\s+");
            long cpuTime = 0;
            for (int i = 1; i <= 7 && i < cpuParts.length; i++) {
                cpuTime += Long.parseLong(cpuParts[i]);
            }

            if (prevCpuTime > 0 && prevAppCpuTime > 0) {
                long cpuDelta = cpuTime - prevCpuTime;
                long appDelta = appCpuTime - prevAppCpuTime;
                if (cpuDelta > 0) {
                    int cores = Runtime.getRuntime().availableProcessors();
                    float percent = (float) appDelta / cpuDelta * 100f * cores;
                    prevCpuTime = cpuTime;
                    prevAppCpuTime = appCpuTime;
                    return Math.min(100f * cores, percent);
                }
            }

            prevCpuTime = cpuTime;
            prevAppCpuTime = appCpuTime;
            return 0f;

        } catch (Exception e) {
            return 0f;
        }
    }

    private float getMemoryUsageMB() {
        try {
            Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memInfo);
            return memInfo.getTotalPss() / 1024f;
        } catch (Exception e) {
            return 0f;
        }
    }

    private float getGpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"));
            String line = reader.readLine();
            reader.close();
            if (line != null) return Float.parseFloat(line.trim());
        } catch (Exception ignored) {}
        return -1f;
    }

    private void startFpsCounter() {
        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!running.get()) return;
                frameCount.incrementAndGet();
                long now = System.currentTimeMillis();
                long lastCalc = lastFpsCalcTime.get();
                if (now - lastCalc >= 1000) {
                    int frames = frameCount.getAndSet(0);
                    float interval = (now - lastCalc) / 1000f;
                    currentFps = frames / interval;
                    lastFpsCalcTime.set(now);
                }
                Choreographer.getInstance().postFrameCallback(this);
            }
        };
        mainHandler.post(() -> Choreographer.getInstance().postFrameCallback(frameCallback));
    }

    private void stopFpsCounter() {
        mainHandler.post(() -> {
            if (frameCallback != null) Choreographer.getInstance().removeFrameCallback(frameCallback);
        });
    }

    private void flushBuffer() throws IOException {
        if (buffer.isEmpty() || writer == null) return;
        StringBuilder sb = new StringBuilder();
        for (MetricSample s : buffer) {
            sb.append(String.format(Locale.US, "%d,%d,%s,%d,%.2f,%.2f,%.2f,%.1f,%d\n",
                    s.timestampMs, s.elapsedMs, s.testName, s.iteration,
                    s.cpuPercent, s.ramMb, s.gpuPercent, s.fps, s.pid));
        }
        writer.write(sb.toString());
        writer.flush();
        buffer.clear();
    }

    private static class MetricSample {
        final long timestampMs;
        final long elapsedMs;
        final String testName;
        final int iteration;
        final float cpuPercent;
        final float ramMb;
        final float gpuPercent;
        final float fps;
        final int pid;

        MetricSample(long timestampMs, long elapsedMs, String testName, int iteration,
                float cpuPercent, float ramMb, float gpuPercent, float fps, int pid) {
            this.timestampMs = timestampMs;
            this.elapsedMs = elapsedMs;
            this.testName = testName;
            this.iteration = iteration;
            this.cpuPercent = cpuPercent;
            this.ramMb = ramMb;
            this.gpuPercent = gpuPercent;
            this.fps = fps;
            this.pid = pid;
        }
    }
}
