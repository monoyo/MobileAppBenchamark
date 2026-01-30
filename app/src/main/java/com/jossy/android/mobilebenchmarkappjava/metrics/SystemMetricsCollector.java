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
 * 
 * Eliminuje potrzebę zewnętrznego skryptu Python!
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

    // Metryki CPU
    private long prevCpuTime = 0;
    private long prevAppCpuTime = 0;

    // Metryki FPS
    private final AtomicInteger frameCount = new AtomicInteger(0);
    private final AtomicLong lastFpsCalcTime = new AtomicLong(0);
    private volatile float currentFps = 0f;

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

    /**
     * Rozpoczyna zbieranie metryk.
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            return; // Już działa
        }

        // Otwórz plik CSV
        if (!outputFile.getParentFile().exists()) {
            outputFile.getParentFile().mkdirs();
        }
        writer = new BufferedWriter(new FileWriter(outputFile), 32 * 1024);
        writer.write("TimestampMs,ElapsedMs,TestName,Iteration,CPU%,RAM_MB,GPU%,FPS,PID\n");

        startTimeMs = System.currentTimeMillis();
        lastFpsCalcTime.set(startTimeMs);

        // Uruchom FPS counter na main thread
        startFpsCounter();

        // Uruchom sampling w tle
        executor.submit(this::samplingLoop);

        Log.i(TAG, "Started metrics collection: " + outputFile.getAbsolutePath());
    }

    /**
     * Zatrzymuje zbieranie metryk.
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        // Zatrzymaj FPS counter
        stopFpsCounter();

        // Zatrzymaj executor
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

        // Zamknij plik
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

    /**
     * Ustawia aktualny test (dla synchronizacji danych).
     */
    public void setCurrentTest(String testName, int iteration) {
        this.currentTestName = testName;
        this.currentIteration = iteration;
    }

    /**
     * Główna pętla próbkowania.
     */
    private void samplingLoop() {
        while (running.get()) {
            long loopStart = System.currentTimeMillis();

            try {
                MetricSample sample = collectSample();
                synchronized (buffer) {
                    buffer.add(sample);
                    if (buffer.size() >= BUFFER_SIZE) {
                        flushBuffer();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Sampling error", e);
            }

            // Czekaj do następnego interwału
            long elapsed = System.currentTimeMillis() - loopStart;
            long sleepMs = Math.max(1, samplingIntervalMs - elapsed);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    /**
     * Zbiera pojedynczą próbkę metryk.
     */
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

    /**
     * Oblicza CPU% dla procesu aplikacji.
     */
    private float getCpuUsage() {
        try {
            // Odczyt /proc/[pid]/stat dla naszego procesu
            int pid = Process.myPid();
            RandomAccessFile procStat = new RandomAccessFile("/proc/" + pid + "/stat", "r");
            String statLine = procStat.readLine();
            procStat.close();

            String[] parts = statLine.split("\\s+");
            if (parts.length < 15)
                return 0f;

            long utime = Long.parseLong(parts[13]);
            long stime = Long.parseLong(parts[14]);
            long appCpuTime = utime + stime;

            // Odczyt /proc/stat dla całego systemu
            RandomAccessFile cpuStat = new RandomAccessFile("/proc/stat", "r");
            String cpuLine = cpuStat.readLine();
            cpuStat.close();

            String[] cpuParts = cpuLine.split("\\s+");
            long cpuTime = 0;
            for (int i = 1; i <= 7 && i < cpuParts.length; i++) {
                cpuTime += Long.parseLong(cpuParts[i]);
            }

            // Oblicz procent
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

    /**
     * Pobiera zużycie RAM w MB.
     */
    private float getMemoryUsageMB() {
        try {
            // Metoda 1: Debug.MemoryInfo (dokładniejsza)
            Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memInfo);
            int totalPss = memInfo.getTotalPss(); // KB
            return totalPss / 1024f;
        } catch (Exception e) {
            try {
                // Metoda 2: ActivityManager (fallback)
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                int[] pids = { Process.myPid() };
                Debug.MemoryInfo[] memInfos = am.getProcessMemoryInfo(pids);
                if (memInfos != null && memInfos.length > 0) {
                    return memInfos[0].getTotalPss() / 1024f;
                }
            } catch (Exception e2) {
                // Ignoruj
            }
        }
        return 0f;
    }

    /**
     * Próbuje pobrać GPU usage (zależne od urządzenia).
     */
    private float getGpuUsage() {
        // Próba dla Qualcomm Adreno
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"));
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                return Float.parseFloat(line.trim());
            }
        } catch (Exception ignored) {
        }

        // Próba dla Mali
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("/sys/class/misc/mali0/device/utilization"));
            String line = reader.readLine();
            reader.close();
            if (line != null) {
                return Float.parseFloat(line.trim());
            }
        } catch (Exception ignored) {
        }

        // GPU usage niedostępne na tym urządzeniu
        return -1f;
    }

    /**
     * Uruchamia licznik FPS przez Choreographer.
     */
    private void startFpsCounter() {
        frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!running.get())
                    return;

                frameCount.incrementAndGet();

                // Oblicz FPS co sekundę
                long now = System.currentTimeMillis();
                long lastCalc = lastFpsCalcTime.get();
                if (now - lastCalc >= 1000) {
                    int frames = frameCount.getAndSet(0);
                    float interval = (now - lastCalc) / 1000f;
                    currentFps = frames / interval;
                    lastFpsCalcTime.set(now);
                }

                // Kontynuuj
                Choreographer.getInstance().postFrameCallback(this);
            }
        };

        mainHandler.post(() -> Choreographer.getInstance().postFrameCallback(frameCallback));
    }

    /**
     * Zatrzymuje licznik FPS.
     */
    private void stopFpsCounter() {
        mainHandler.post(() -> {
            if (frameCallback != null) {
                Choreographer.getInstance().removeFrameCallback(frameCallback);
            }
        });
    }

    /**
     * Zapisuje bufor do pliku.
     */
    private void flushBuffer() throws IOException {
        if (buffer.isEmpty() || writer == null)
            return;

        StringBuilder sb = new StringBuilder();
        for (MetricSample s : buffer) {
            sb.append(String.format(Locale.US,
                    "%d,%d,%s,%d,%.2f,%.2f,%.2f,%.1f,%d\n",
                    s.timestampMs, s.elapsedMs, s.testName, s.iteration,
                    s.cpuPercent, s.ramMb, s.gpuPercent, s.fps, s.pid));
        }
        writer.write(sb.toString());
        writer.flush();
        buffer.clear();
    }

    /**
     * Struktura pojedynczej próbki.
     */
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
