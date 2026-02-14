package com.jossy.android.mobilebenchmarkappkotlin.utils

import android.util.Log
import com.jossy.android.mobilebenchmarkappkotlin.data.TestEntry
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.Writer

class BufferedCsvWriter(
    private val outputFile: File,
    private val bufferCapacity: Int,
    private val writeBufferBytes: Int,
    private val testName: String = "unknown"
) : AutoCloseable {

    companion object {
        private const val TAG = "BufferedCsvWriter"
    }

    private val checkpointFile =
        File(outputFile.parentFile, outputFile.name.replace(".csv", "_checkpoint.csv"))
    private val buffer = mutableListOf<TestEntry>()

    private val mutex = Mutex()

    // Scope z osobnym wątkiem (Dispatcherem) do zapisu
    private val writeScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("csv-writer"))

    private var totalWritten = 0
    private var writer: Writer? = null
    private var closed = false
    private var lastFlushTimeMs = System.currentTimeMillis()

    fun initialize() {
        runBlocking {
            mutex.withLock {
                if (writer != null) return@withLock

                outputFile.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw Exception("Cannot create directory: ${parent.absolutePath}")
                    }
                }

                writer = outputFile.bufferedWriter(bufferSize = writeBufferBytes)
                writeHeader()
                Log.i(TAG, "Initialized CSV writer: ${outputFile.name}")
            }
        }
    }

    fun write(entry: TestEntry) {
        writeScope.launch {
            mutex.withLock {
                if (closed) return@withLock

                buffer.add(entry)

                val timeSinceFlush = System.currentTimeMillis() - lastFlushTimeMs
                val shouldFlush = buffer.size >= bufferCapacity ||
                        (timeSinceFlush > 30_000 && buffer.isNotEmpty())

                if (shouldFlush) {
                    flushAsyncInternal()
                }
            }
        }
    }

    fun flush() {
        runBlocking {
            mutex.withLock {
                try {
                    flushBufferToDisk()
                } catch (e: Exception) {
                    saveCheckpoint()
                }
            }
        }
    }

    private fun flushAsyncInternal() {
        if (closed) return

        // Kopiujemy dane
        val toWrite = buffer.toList()
        buffer.clear()
        lastFlushTimeMs = System.currentTimeMillis()

        if (toWrite.isEmpty()) return

        try {
            writeEntries(toWrite)
            totalWritten += toWrite.size
            writer?.flush()
        } catch (e: Exception) {
            saveCheckpointInternal(toWrite)
        }
    }

    private fun flushBufferToDisk() {
        if (buffer.isEmpty()) return
        val toWrite = buffer.toList()
        buffer.clear()
        writeEntries(toWrite)
        totalWritten += toWrite.size
        writer?.flush()
    }

    private fun writeEntries(entries: List<TestEntry>) {
        val currentWriter = writer ?: return
        val csvContent = buildString {
            entries.forEach { entry ->
                append(entry.iteration).append(',')
                append(entry.result.executionTimeMs).append(',')
                append(csvEscape(entry.result.details)).append(',')
                append(entry.intervalStartMs).append(',')
                append(entry.intervalDurationMs).append(',')
                append(entry.cumulativeTimeMs).append('\n')
            }
        }
        currentWriter.write(csvContent)
    }

    private fun writeHeader() {
        writer?.write("iteration,execution_time_ms,details,interval_start_ms,interval_duration_ms,cumulative_time_ms\n")
    }

    private fun saveCheckpoint() {
        saveCheckpointInternal(buffer.toList())
    }

    private fun saveCheckpointInternal(entries: List<TestEntry>) {
        if (entries.isEmpty()) return
        try {
            checkpointFile.appendText(buildString {
                entries.forEach { entry ->
                    append("${entry.iteration},${entry.result.executionTimeMs}," +
                            "${csvEscape(entry.result.details)},${entry.intervalStartMs}," +
                            "${entry.intervalDurationMs},${entry.cumulativeTimeMs}\n")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save checkpoint", e)
        }
    }

    private fun csvEscape(value: String?): String {
        val v = value ?: return ""
        val needsQuote = v.any { it in ",\"\n\r" }
        return if (needsQuote) "\"${v.replace("\"", "\"\"")}\"" else v
    }

    override fun close() {
        runBlocking {
            mutex.withLock {
                if (closed) return@withLock
                closed = true

                if (buffer.isNotEmpty()) {
                    try {
                        flushBufferToDisk()
                    } catch (e: Exception) {
                        saveCheckpointInternal(buffer.toList())
                    }
                }

                try {
                    writer?.close()
                } catch (ignored: Exception) {
                } finally {
                    writer = null
                }

                if (checkpointFile.exists()) {
                    checkpointFile.delete()
                }
            }
            // Anulujemy scope po wykonaniu wszystkich operacji
            writeScope.cancel()
        }
    }
}