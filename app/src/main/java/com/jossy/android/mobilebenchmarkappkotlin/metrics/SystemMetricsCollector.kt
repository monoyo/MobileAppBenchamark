package com.jossy.android.mobilebenchmarkappkotlin.metrics

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Choreographer
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Kolektor metryk systemowych - zbiera CPU, RAM, GPU, FPS bezpośrednio w aplikacji.
 * Zapisuje dane do CSV w czasie rzeczywistym z synchronizacją testów.
 */
class SystemMetricsCollector(
    context: Context,
    private val outputDir: File,
    private val sessionId: String
) {
    companion object {
        private const val TAG = "SystemMetricsCollector"
        private const val SAMPLING_INTERVAL_MS = 100L
        private const val BUFFER_SIZE = 50
    }

    private val appContext = context.applicationContext
    private val outputFile = File(outputDir, "system_metrics_$sessionId.csv")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Stan
    private val running = AtomicBoolean(false)
    @Volatile private var currentTestName = "IDLE"
    @Volatile private var currentIteration = 0
    private var startTimeMs = 0L

    // Metryki CPU
    private var prevCpuTime = 0L
    private var prevAppCpuTime = 0L

    // Metryki FPS
    private val frameCount = AtomicInteger(0)
    private val lastFpsCalcTime = AtomicLong(0)
    @Volatile private var currentFps = 0f

    // Bufor i writer
    private var writer: BufferedWriter? = null
    private val buffer = mutableListOf<MetricSample>()

    // Choreographer callback
    private var frameCallback: Choreographer.FrameCallback? = null

    data class MetricSample(
        val timestampMs: Long,
        val elapsedMs: Long,
        val testName: String,
        val iteration: Int,
        val cpuPercent: Float,
        val ramMb: Float,
        val gpuPercent: Float,
        val fps: Float,
        val pid: Int
    )

    /**
     * Rozpoczyna zbieranie metryk.
     */
    fun start() {
        if (running.getAndSet(true)) return

        try {
            outputDir.mkdirs()
            writer = BufferedWriter(FileWriter(outputFile), 32 * 1024)
            writer?.write("TimestampMs,ElapsedMs,TestName,Iteration,CPU%,RAM_MB,GPU%,FPS,PID\n")

            startTimeMs = System.currentTimeMillis()
            lastFpsCalcTime.set(startTimeMs)

            startFpsCounter()
            scope.launch { samplingLoop() }

            Log.i(TAG, "Started: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Start failed: ${e.message}", e)
            running.set(false)
        }
    }

    /**
     * Zatrzymuje zbieranie metryk.
     */
    fun stop() {
        if (!running.getAndSet(false)) return

        stopFpsCounter()
        scope.cancel()

        try {
            synchronized(buffer) {
                flushBuffer()
            }
            writer?.close()
            writer = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}", e)
        }

        Log.i(TAG, "Stopped")
    }

    /**
     * Ustawia aktualny test dla synchronizacji.
     */
    fun setCurrentTest(testName: String, iteration: Int) {
        currentTestName = testName
        currentIteration = iteration
    }

    private suspend fun samplingLoop() {
        while (running.get()) {
            val loopStart = System.currentTimeMillis()

            try {
                val sample = collectSample()
                synchronized(buffer) {
                    buffer.add(sample)
                    if (buffer.size >= BUFFER_SIZE) {
                        flushBuffer()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sampling error: ${e.message}")
            }

            val elapsed = System.currentTimeMillis() - loopStart
            val sleepMs = maxOf(0, SAMPLING_INTERVAL_MS - elapsed)
            if (sleepMs > 0) delay(sleepMs)
        }
    }

    private fun collectSample(): MetricSample {
        val now = System.currentTimeMillis()
        return MetricSample(
            timestampMs = now,
            elapsedMs = now - startTimeMs,
            testName = currentTestName,
            iteration = currentIteration,
            cpuPercent = getCpuUsage(),
            ramMb = getMemoryUsageMB(),
            gpuPercent = getGpuUsage(),
            fps = currentFps,
            pid = Process.myPid()
        )
    }

    private fun getCpuUsage(): Float {
        return try {
            val pid = Process.myPid()

            // /proc/[pid]/stat
            RandomAccessFile("/proc/$pid/stat", "r").use { procStat ->
                val statLine = procStat.readLine()
                val parts = statLine.split("\\s+".toRegex())
                if (parts.size < 15) return 0f

                val utime = parts[13].toLong()
                val stime = parts[14].toLong()
                val appCpuTime = utime + stime

                // /proc/stat
                RandomAccessFile("/proc/stat", "r").use { cpuStat ->
                    val cpuLine = cpuStat.readLine()
                    val cpuParts = cpuLine.split("\\s+".toRegex())
                    val cpuTime = (1..7).sumOf { cpuParts.getOrNull(it)?.toLongOrNull() ?: 0L }

                    if (prevCpuTime > 0 && prevAppCpuTime > 0) {
                        val cpuDelta = cpuTime - prevCpuTime
                        val appDelta = appCpuTime - prevAppCpuTime
                        if (cpuDelta > 0) {
                            val cores = Runtime.getRuntime().availableProcessors()
                            val percent = appDelta.toFloat() / cpuDelta * 100f * cores
                            prevCpuTime = cpuTime
                            prevAppCpuTime = appCpuTime
                            return minOf(100f * cores, percent)
                        }
                    }

                    prevCpuTime = cpuTime
                    prevAppCpuTime = appCpuTime
                    0f
                }
            }
        } catch (e: Exception) {
            0f
        }
    }

    private fun getMemoryUsageMB(): Float {
        return try {
            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            memInfo.totalPss / 1024f
        } catch (e: Exception) {
            0f
        }
    }

    private fun getGpuUsage(): Float {
        // Qualcomm Adreno
        try {
            File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").readText().trim().toFloatOrNull()?.let { return it }
        } catch (_: Exception) {}

        // Mali
        try {
            File("/sys/class/misc/mali0/device/utilization").readText().trim().toFloatOrNull()?.let { return it }
        } catch (_: Exception) {}

        return -1f
    }

    private fun startFpsCounter() {
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running.get()) return

                frameCount.incrementAndGet()

                val now = System.currentTimeMillis()
                val lastCalc = lastFpsCalcTime.get()
                if (now - lastCalc >= 1000) {
                    val frames = frameCount.getAndSet(0)
                    val interval = (now - lastCalc) / 1000f
                    currentFps = frames / interval
                    lastFpsCalcTime.set(now)
                }

                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        mainHandler.post {
            Choreographer.getInstance().postFrameCallback(frameCallback!!)
        }
    }

    private fun stopFpsCounter() {
        mainHandler.post {
            frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        }
    }

    private fun flushBuffer() {
        if (buffer.isEmpty() || writer == null) return

        try {
            val sb = StringBuilder()
            buffer.forEach { s ->
                sb.append(String.format(Locale.US,
                    "%d,%d,%s,%d,%.2f,%.2f,%.2f,%.1f,%d\n",
                    s.timestampMs, s.elapsedMs, s.testName, s.iteration,
                    s.cpuPercent, s.ramMb, s.gpuPercent, s.fps, s.pid))
            }
            writer?.write(sb.toString())
            writer?.flush()
            buffer.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Flush error: ${e.message}")
        }
    }
}
