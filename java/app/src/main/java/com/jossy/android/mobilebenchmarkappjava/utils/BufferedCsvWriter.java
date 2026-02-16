package com.jossy.android.mobilebenchmarkappjava.io;

import android.util.Log;

import com.jossy.android.mobilebenchmarkappjava.data.TestEntry;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Buforowany writer CSV zoptymalizowany pod kątem dużych zbiorów danych (do 1M
 * próbek).
 */
public class BufferedCsvWriter implements AutoCloseable {

    private static final String TAG = "BufferedCsvWriter";

    private final File outputFile;
    private final File checkpointFile;
    private final int bufferCapacity;
    private final int writeBufferBytes;
    private final List<TestEntry> buffer;
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService writeExecutor;
    private final AtomicInteger totalWritten = new AtomicInteger(0);
    private final AtomicInteger totalBuffered = new AtomicInteger(0);

    private BufferedWriter writer;
    private boolean closed = false;
    private long lastFlushTimeMs = System.currentTimeMillis();
    private volatile Throwable lastError = null;

    private final String testName;

    public BufferedCsvWriter(File outputFile, int bufferCapacity, int writeBufferBytes) {
        this(outputFile, bufferCapacity, writeBufferBytes, "unknown");
    }

    public interface CsvFormatter {
        String format(TestEntry entry);

        String getHeader();
    }

    private final CsvFormatter formatter;

    public BufferedCsvWriter(File outputFile, int bufferCapacity, int writeBufferBytes, String testName) {
        this(outputFile, bufferCapacity, writeBufferBytes, testName, null);
    }

    public BufferedCsvWriter(File outputFile, int bufferCapacity, int writeBufferBytes, String testName,
            CsvFormatter formatter) {
        this.outputFile = outputFile;
        this.checkpointFile = new File(outputFile.getParent(),
                outputFile.getName().replace(".csv", "_checkpoint.csv"));
        this.bufferCapacity = bufferCapacity;
        this.writeBufferBytes = writeBufferBytes;
        this.testName = testName;
        this.formatter = formatter != null ? formatter : new DefaultCsvFormatter();
        this.buffer = new ArrayList<>(bufferCapacity);
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "csv-writer-" + outputFile.getName());
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    private class DefaultCsvFormatter implements CsvFormatter {
        @Override
        public String format(TestEntry entry) {
            return entry.iteration + "," +
                    entry.result.getExecutionTimeMs() + "," +
                    csvEscape(entry.result.getDetails()) + "," +
                    entry.intervalStartMs + "," +
                    entry.intervalDurationMs + "," +
                    entry.cumulativeTimeMs + "\n";
        }

        @Override
        public String getHeader() {
            return "iteration,execution_time_ms,details,interval_start_ms,interval_duration_ms,cumulative_time_ms\n";
        }
    }

    public void initialize() throws IOException {
        lock.lock();
        try {
            if (writer != null)
                return;
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Cannot create directory: " + parent.getAbsolutePath());
            }
            writer = new BufferedWriter(new FileWriter(outputFile), writeBufferBytes);
            writeHeader();
            Log.i(TAG, "Initialized CSV writer: " + outputFile.getName());
        } finally {
            lock.unlock();
        }
    }

    public void write(TestEntry entry) {
        lock.lock();
        try {
            if (closed)
                return;

            buffer.add(entry);
            totalBuffered.incrementAndGet();

            boolean shouldFlush = buffer.size() >= bufferCapacity ||
                    (System.currentTimeMillis() - lastFlushTimeMs > 30_000 && !buffer.isEmpty());

            if (shouldFlush) {
                flushAsync();
            }
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        lock.lock();
        try {
            flushBufferInternal();
        } catch (IOException e) {
            lastError = e;
            saveCheckpoint();
        } finally {
            lock.unlock();
        }
    }

    private void flushAsync() {
        if (closed)
            return;

        final List<TestEntry> toWrite = new ArrayList<>(buffer);
        buffer.clear();
        lastFlushTimeMs = System.currentTimeMillis();

        if (toWrite.isEmpty())
            return;

        try {
            writeExecutor.submit(() -> {
                lock.lock();
                try {
                    if (writer == null)
                        return;
                    writeEntries(toWrite);
                    totalWritten.addAndGet(toWrite.size());
                    writer.flush();
                } catch (IOException e) {
                    lastError = e;
                    saveCheckpointInternal(toWrite);
                } finally {
                    lock.unlock();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to submit async write", e);
        }
    }

    private void flushBufferInternal() throws IOException {
        if (buffer.isEmpty())
            return;
        List<TestEntry> toWrite = new ArrayList<>(buffer);
        buffer.clear();
        writeEntries(toWrite);
        totalWritten.addAndGet(toWrite.size());
        if (writer != null)
            writer.flush();
    }

    private void writeEntries(List<TestEntry> entries) throws IOException {
        if (writer == null)
            throw new IOException("Writer not initialized");
        StringBuilder sb = new StringBuilder(entries.size() * 120);
        for (TestEntry entry : entries) {
            sb.append(formatter.format(entry));
        }
        writer.write(sb.toString());
    }

    private void writeHeader() throws IOException {
        if (writer != null) {
            writer.write(formatter.getHeader());
        }
    }

    public void saveCheckpoint() {
        lock.lock();
        try {
            saveCheckpointInternal(buffer);
        } finally {
            lock.unlock();
        }
    }

    private void saveCheckpointInternal(List<TestEntry> entries) {
        if (entries == null || entries.isEmpty())
            return;
        try (BufferedWriter cpWriter = new BufferedWriter(new FileWriter(checkpointFile, true))) {
            for (TestEntry entry : entries) {
                cpWriter.write(entry.iteration + "," + entry.result.getExecutionTimeMs() + "," +
                        csvEscape(entry.result.getDetails()) + "," + entry.intervalStartMs + "," +
                        entry.intervalDurationMs + "," + entry.cumulativeTimeMs + "\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save checkpoint", e);
        }
    }

    private String csvEscape(String value) {
        if (value == null)
            return "";
        boolean needsQuote = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? ("\"" + escaped + "\"") : escaped;
    }

    @Override
    public void close() {
        boolean alreadyClosed;
        lock.lock();
        try {
            alreadyClosed = closed;
            closed = true;
        } finally {
            lock.unlock();
        }

        if (alreadyClosed)
            return;

        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        lock.lock();
        try {
            if (!buffer.isEmpty()) {
                try {
                    flushBufferInternal();
                } catch (IOException e) {
                    saveCheckpointInternal(buffer);
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                } finally {
                    writer = null;
                }
            }
            if (lastError == null && checkpointFile.exists()) {
                checkpointFile.delete();
            }
        } finally {
            lock.unlock();
        }
    }
}
