package com.jossy.android.mobilebenchmarkappkotlin.io

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Buforowany writer CSV z asynchronicznym zapisem i checkpointami.
 * Optymalizowany dla dużych zbiorów danych (do 1M próbek).
 *
 * @param file Plik docelowy CSV
 * @param bufferSize Liczba rekordów przed automatycznym flush
 * @param writeBufferBytes Rozmiar bufora I/O w bajtach
 */
class BufferedCsvWriter(
    private val file: File,
    private val bufferSize: Int = 1000,
    private val writeBufferBytes: Int = 64 * 1024
) {
    companion object {
        private const val TAG = "BufferedCsvWriter"
    }

    private var writer: BufferedWriter? = null
    private val buffer = mutableListOf<String>()
    private val lock = ReentrantLock()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var checkpointFile: File? = null
    private var initialized = false

    /**
     * Inicjalizuje writer i zapisuje header CSV.
     */
    @Throws(IOException::class)
    fun initialize(header: String = "iteration,executionTimeMs,details,intervalStartMs,intervalDurationMs,cumulativeTimeMs") {
        lock.withLock {
            if (initialized) return

            file.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }

            writer = BufferedWriter(FileWriter(file), writeBufferBytes)
            writer?.apply {
                write(header)
                newLine()
                flush()
            }

            // Checkpoint file dla recovery
            checkpointFile = File(file.parentFile, "${file.nameWithoutExtension}_checkpoint.csv")

            initialized = true
            Log.i(TAG, "Initialized: ${file.absolutePath}")
        }
    }

    /**
     * Dodaje rekord do bufora. Auto-flush gdy bufor pełny.
     */
    fun write(
        iteration: Int,
        executionTimeMs: Long,
        details: String,
        intervalStartMs: Long = 0,
        intervalDurationMs: Long = 0,
        cumulativeTimeMs: Long = 0
    ) {
        val escapedDetails = escapeCSV(details)
        val line = "$iteration,$executionTimeMs,$escapedDetails,$intervalStartMs,$intervalDurationMs,$cumulativeTimeMs"

        lock.withLock {
            buffer.add(line)
            if (buffer.size >= bufferSize) {
                flushInternal()
            }
        }
    }

    /**
     * Wymusza zapis bufora na dysk.
     */
    fun flush() {
        lock.withLock {
            flushInternal()
        }
    }

    /**
     * Asynchroniczny flush w tle.
     */
    fun flushAsync() {
        scope.launch {
            flush()
        }
    }

    private fun flushInternal() {
        if (buffer.isEmpty()) return

        try {
            writer?.apply {
                buffer.forEach { line ->
                    write(line)
                    newLine()
                }
                flush()
            }

            // Aktualizuj checkpoint
            saveCheckpoint()

            buffer.clear()
        } catch (e: IOException) {
            Log.e(TAG, "Flush error: ${e.message}", e)
            // Checkpoint powinien zachować poprzednie dane
        }
    }

    private fun saveCheckpoint() {
        try {
            checkpointFile?.let { cp ->
                file.copyTo(cp, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Checkpoint save failed: ${e.message}")
        }
    }

    /**
     * Zamyka writer (flush + close).
     */
    fun close() {
        lock.withLock {
            try {
                flushInternal()
                writer?.close()
                writer = null
                initialized = false
                scope.cancel()

                // Usuń checkpoint po pomyślnym zamknięciu
                checkpointFile?.delete()

                Log.i(TAG, "Closed: ${file.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Close error: ${e.message}", e)
            }
        }
    }

    private fun escapeCSV(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}
