package com.jossy.android.mobilebenchmarkappkotlin.metrics

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Choreographer
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Kolektor metryk systemowych - zbiera CPU, RAM, GPU, FPS bezpośrednio z aplikacji.
 * Zapisuje dane do CSV w czasie rzeczywistym.
 */
class SystemMetricsCollector(
    context: Context,
    outputDir: File,
    sessionId: String,
    private val samplingIntervalMs: Int
) {

    companion object {
        private const val TAG = "SystemMetricsCollector"
        private const val BUFFER_SIZE = 50 // Flush co 50 próbek
    }

    private val context = context.applicationContext
    private val outputFile = File(outputDir, "system_metrics_$sessionId.csv")
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "metrics-collector").apply { priority = Thread.MIN_PRIORITY }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val running = AtomicBoolean(false)
    private var currentTestName = "IDLE"
    private var currentIteration = 0
    private var startTimeMs = 0L

    @Volatile
    private var lastCpuUsage = 0f

    @Volatile
    private var lastMemoryUsage = 0f

    @Volatile
    private var currentFps = 0f

    private var prevCpuTime = 0L
    private var prevAppCpuTime = 0L

    private val frameCount = AtomicInteger(0)
    private val lastFpsCalcTime = AtomicLong(0)

    private var writer: BufferedWriter? = null
    private val buffer = mutableListOf<MetricSample>()

    private var frameCallback: Choreographer.FrameCallback? = null

    fun start() {
        if (running.getAndSet(true)) return

        outputFile.parentFile?.apply { if (!exists()) mkdirs() }
        writer = BufferedWriter(FileWriter(outputFile), 32 * 1024)
        writer?.write("TimestampMs,ElapsedMs,TestName,Iteration,CPU%,RAM_MB,GPU%,FPS,PID\n")

        startTimeMs = System.currentTimeMillis()
        lastFpsCalcTime.set(startTimeMs)

        startFpsCounter()
        executor.submit(::samplingLoop)

        Log.i(TAG, "Started metrics collection: ${outputFile.absolutePath}")
    }

    fun stop() {
        if (!running.getAndSet(false)) return

        stopFpsCounter()
        executor.shutdown()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (ignored: InterruptedException) {
        }

        try {
            synchronized(buffer) {
                flushBuffer()
            }
            writer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing writer", e)
        }

        Log.i(TAG, "Stopped metrics collection")
    }

    fun setCurrentTest(testName: String, iteration: Int) {
        currentTestName = testName
        currentIteration = iteration
    }

    fun getLastCpuUsage(): Float = lastCpuUsage

    fun getLastMemoryUsage(): Float = lastMemoryUsage

    fun getLastFps(): Float = currentFps

    private fun samplingLoop() {
        while (running.get()) {
            val loopStart = System.currentTimeMillis()

            try {
                val sample = collectSample()
                lastCpuUsage = sample.cpuPercent
                lastMemoryUsage = sample.ramMb

                synchronized(buffer) {
                    buffer.add(sample)
                    if (buffer.size >= BUFFER_SIZE) {
                        flushBuffer()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sampling error", e)
            }

            val elapsed = System.currentTimeMillis() - loopStart
            val sleepMs = maxOf(1, (samplingIntervalMs - elapsed).toInt()).toLong()
            try {
                Thread.sleep(sleepMs)
            } catch (ignored: InterruptedException) {
                break
            }
        }
    }

    private fun collectSample(): MetricSample {
        val now = System.currentTimeMillis()
        return MetricSample(
            now,
            now - startTimeMs,
            currentTestName,
            currentIteration,
            getCpuUsage(),
            getMemoryUsageMB(),
            getGpuUsage(),
            currentFps,
            Process.myPid()
        )
    }

    private fun getCpuUsage(): Float = try {
        val pid = Process.myPid()
        RandomAccessFile("/proc/$pid/stat", "r").use { procStat ->
            val statLine = procStat.readLine() ?: return 0f
            val parts = statLine.split("\\s+".toRegex()).toTypedArray()

            if (parts.size < 15) return 0f

            val utime = parts[13].toLong()
            val stime = parts[14].toLong()
            val appCpuTime = utime + stime

            RandomAccessFile("/proc/stat", "r").use { cpuStat ->
                val cpuLine = cpuStat.readLine() ?: return 0f
                val cpuParts = cpuLine.split("\\s+".toRegex()).toTypedArray()

                var cpuTime = 0L
                for (i in 1..7) {
                    if (i < cpuParts.size) {
                        cpuTime += cpuParts[i].toLongOrNull() ?: 0L
                    }
                }

                if (prevCpuTime > 0 && prevAppCpuTime > 0) {
                    val cpuDelta = cpuTime - prevCpuTime
                    val appDelta = appCpuTime - prevAppCpuTime
                    if (cpuDelta > 0) {
                        val cores = Runtime.getRuntime().availableProcessors()
                        val percent = (appDelta.toFloat() / cpuDelta) * 100f * cores
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

    private fun getMemoryUsageMB(): Float = try {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        memInfo.totalPss / 1024f
    } catch (e: Exception) {
        0f
    }

    private fun getGpuUsage(): Float = try {
        BufferedReader(FileReader("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")).use { reader ->
            reader.readLine()?.toFloatOrNull() ?: -1f
        }
    } catch (e: Exception) {
        -1f
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
        mainHandler.post { Choreographer.getInstance().postFrameCallback(frameCallback!!) }
    }

    private fun stopFpsCounter() {
        mainHandler.post {
            frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        }
    }

    private fun flushBuffer() {
        if (buffer.isEmpty() || writer == null) return
        val sb = StringBuilder()
        buffer.forEach { s ->
            sb.append(
                String.format(
                    Locale.US,
                    "%d,%d,%s,%d,%.2f,%.2f,%.2f,%.1f,%d\n",
                    s.timestampMs, s.elapsedMs, s.testName, s.iteration,
                    s.cpuPercent, s.ramMb, s.gpuPercent, s.fps, s.pid
                )
            )
        }
        writer?.write(sb.toString())
        writer?.flush()
        buffer.clear()
    }

    private data class MetricSample(
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
}
