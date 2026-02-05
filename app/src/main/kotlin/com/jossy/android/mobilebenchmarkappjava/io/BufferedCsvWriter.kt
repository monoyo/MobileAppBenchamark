package com.jossy.android.mobilebenchmarkappjava.io

import android.util.Log
import com.jossy.android.mobilebenchmarkappjava.data.TestEntry
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * Buforowany writer CSV zoptymalizowany pod kątem dużych zbiorów danych (do 1M próbek).
 */
class BufferedCsvWriter(
    private val outputFile: File,
    private val bufferCapacity: Int,
    private val writeBufferBytes: Int
) : AutoCloseable {

    companion object {
        private const val TAG = "BufferedCsvWriter"
    }

    private val checkpointFile = File(outputFile.parentFile, outputFile.name.replace(".csv", "_checkpoint.csv"))
    private val buffer = mutableListOf<TestEntry>()
    private val lock = ReentrantLock()
    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "csv-writer-${outputFile.name}").apply {
            priority = Thread.MIN_PRIORITY
        }
    }
    private val totalWritten = AtomicInteger(0)
    private val totalBuffered = AtomicInteger(0)

    private var writer: BufferedWriter? = null
    private var closed = false
    private var lastFlushTimeMs = System.currentTimeMillis()
    @Volatile
    private var lastError: Throwable? = null

    fun initialize() {
        lock.withLock {
            if (writer != null) return

            val parent = outputFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw Exception("Cannot create directory: ${parent.absolutePath}")
            }

            writer = BufferedWriter(FileWriter(outputFile), writeBufferBytes)
            writeHeader()
            Log.i(TAG, "Initialized CSV writer: ${outputFile.name}")
        }
    }

    fun write(entry: TestEntry) {
        lock.withLock {
            if (closed) return

            buffer.add(entry)
            totalBuffered.incrementAndGet()

            val shouldFlush = buffer.size >= bufferCapacity ||
                    (System.currentTimeMillis() - lastFlushTimeMs > 30_000 && buffer.isNotEmpty())

            if (shouldFlush) {
                flushAsync()
            }
        }
    }

    fun flush() {
        lock.withLock {
            try {
                flushBufferInternal()
            } catch (e: Exception) {
                lastError = e
                saveCheckpoint()
            }
        }
    }

    private fun flushAsync() {
        if (closed) return

        val toWrite = buffer.toList()
        buffer.clear()
        lastFlushTimeMs = System.currentTimeMillis()

        if (toWrite.isEmpty()) return

        try {
            writeExecutor.submit {
                lock.withLock {
                    if (writer == null) return@submit
                    try {
                        writeEntries(toWrite)
                        totalWritten.addAndGet(toWrite.size)
                        writer?.flush()
                    } catch (e: Exception) {
                        lastError = e
                        saveCheckpointInternal(toWrite)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit async write", e)
        }
    }

    private fun flushBufferInternal() {
        if (buffer.isEmpty()) return
        val toWrite = buffer.toList()
        buffer.clear()
        writeEntries(toWrite)
        totalWritten.addAndGet(toWrite.size)
        writer?.flush()
    }

    private fun writeEntries(entries: List<TestEntry>) {
        val writer = writer ?: throw Exception("Writer not initialized")
        val sb = StringBuilder(entries.size * 100)
        entries.forEach { entry ->
            sb.append(entry.iteration).append(',')
                .append(entry.result.executionTime).append(',')
                .append(csvEscape(entry.result.details)).append(',')
                .append(entry.intervalStartMs).append(',')
                .append(entry.intervalDurationMs).append(',')
                .append(entry.cumulativeTimeMs).append('\n')
        }
        writer.write(sb.toString())
    }

    private fun writeHeader() {
        writer?.write("iteration,executionTimeMs,details,intervalStartMs,intervalDurationMs,cumulativeTimeMs\n")
    }

    fun saveCheckpoint() {
        lock.withLock {
            saveCheckpointInternal(buffer.toList())
        }
    }

    private fun saveCheckpointInternal(entries: List<TestEntry>) {
        if (entries.isEmpty()) return
        try {
            BufferedWriter(FileWriter(checkpointFile, true)).use { cpWriter ->
                entries.forEach { entry ->
                    cpWriter.write(
                        "${entry.iteration},${entry.result.executionTime}," +
                                "${csvEscape(entry.result.details)},${entry.intervalStartMs}," +
                                "${entry.intervalDurationMs},${entry.cumulativeTimeMs}\n"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save checkpoint", e)
        }
    }

    private fun csvEscape(value: String?): String {
        if (value == null) return ""
        val needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    override fun close() {
        val alreadyClosed = lock.withLock {
            val was = closed
            closed = true
            was
        }

        if (alreadyClosed) return

        writeExecutor.shutdown()
        try {
            if (!writeExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            writeExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        lock.withLock {
            if (buffer.isNotEmpty()) {
                try {
                    flushBufferInternal()
                } catch (e: Exception) {
                    saveCheckpointInternal(buffer.toList())
                }
            }
            writer?.let {
                try {
                    it.close()
                } catch (ignored: Exception) {
                } finally {
                    writer = null
                }
            }
            if (lastError == null && checkpointFile.exists()) {
                checkpointFile.delete()
            }
        }
    }
}

private inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}
