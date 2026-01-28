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
 * 
 * Optymalizacje:
 * 1. Buforowanie rekordów w pamięci przed zapisem (zmniejsza liczbę operacji
 * I/O)
 * 2. Asynchroniczny zapis w tle (nie blokuje wątku pomiarowego)
 * 3. Automatyczne checkpointy (odzyskiwanie danych po błędach)
 * 4. Konfigurowalny rozmiar bufora dla różnych scenariuszy
 * 
 * Thread-safety: TAK - wszystkie publiczne metody są thread-safe.
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
    private boolean headerWritten = false;
    private boolean closed = false;
    private long lastFlushTimeMs = System.currentTimeMillis();
    private volatile Throwable lastError = null;

    /**
     * Tworzy nowy BufferedCsvWriter.
     *
     * @param outputFile       Plik docelowy CSV
     * @param bufferCapacity   Liczba rekordów do buforowania przed zapisem
     * @param writeBufferBytes Rozmiar bufora I/O w bajtach (dla BufferedWriter)
     */
    public BufferedCsvWriter(File outputFile, int bufferCapacity, int writeBufferBytes) {
        this.outputFile = outputFile;
        this.checkpointFile = new File(outputFile.getParent(),
                outputFile.getName().replace(".csv", "_checkpoint.csv"));
        this.bufferCapacity = bufferCapacity;
        this.writeBufferBytes = writeBufferBytes;
        this.buffer = new ArrayList<>(bufferCapacity);
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "csv-writer-" + outputFile.getName());
            t.setPriority(Thread.MIN_PRIORITY); // Niski priorytet - nie zakłóca pomiarów
            return t;
        });
    }

    /**
     * Inicjalizuje writer i tworzy plik z nagłówkiem.
     */
    public void initialize() throws IOException {
        lock.lock();
        try {
            if (writer != null) {
                return; // Już zainicjalizowany
            }

            // Upewnij się, że katalog istnieje
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new IOException("Cannot create directory: " + parent.getAbsolutePath());
                }
            }

            writer = new BufferedWriter(new FileWriter(outputFile), writeBufferBytes);
            writeHeader();
            headerWritten = true;
            Log.i(TAG, "Initialized CSV writer: " + outputFile.getName() +
                    " (buffer=" + bufferCapacity + " records, I/O buffer=" + writeBufferBytes + " bytes)");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Dodaje rekord do bufora. Automatycznie flush gdy bufor pełny.
     */
    public void write(TestEntry entry) {
        if (closed) {
            Log.w(TAG, "Attempted to write to closed writer");
            return;
        }

        lock.lock();
        try {
            buffer.add(entry);
            totalBuffered.incrementAndGet();

            // Flush jeśli bufor pełny lub minął interwał czasowy
            boolean shouldFlush = buffer.size() >= bufferCapacity ||
                    (System.currentTimeMillis() - lastFlushTimeMs > 30_000 && !buffer.isEmpty());

            if (shouldFlush) {
                flushAsync();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Wymusza natychmiastowy zapis bufora (synchroniczny).
     */
    public void flush() {
        lock.lock();
        try {
            if (buffer.isEmpty()) {
                return;
            }
            flushBufferInternal();
        } catch (IOException e) {
            lastError = e;
            Log.e(TAG, "Flush error: " + e.getMessage(), e);
            // Próba zapisu do checkpointu
            saveCheckpoint();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Asynchroniczny flush - nie blokuje wątku wywołującego.
     */
    private void flushAsync() {
        // Kopiujemy bufor i czyścimy go natychmiast
        final List<TestEntry> toWrite = new ArrayList<>(buffer);
        buffer.clear();
        lastFlushTimeMs = System.currentTimeMillis();

        writeExecutor.submit(() -> {
            lock.lock();
            try {
                writeEntries(toWrite);
                totalWritten.addAndGet(toWrite.size());

                // Zwalniamy pamięć po zapisie
                toWrite.clear();

                if (writer != null) {
                    writer.flush();
                }
            } catch (IOException e) {
                lastError = e;
                Log.e(TAG, "Async write error: " + e.getMessage(), e);
                saveCheckpointInternal(toWrite);
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Synchroniczny zapis bufora.
     */
    private void flushBufferInternal() throws IOException {
        if (buffer.isEmpty()) {
            return;
        }

        List<TestEntry> toWrite = new ArrayList<>(buffer);
        buffer.clear();
        lastFlushTimeMs = System.currentTimeMillis();

        writeEntries(toWrite);
        totalWritten.addAndGet(toWrite.size());
        toWrite.clear();

        if (writer != null) {
            writer.flush();
        }
    }

    /**
     * Zapisuje listę rekordów do pliku.
     */
    private void writeEntries(List<TestEntry> entries) throws IOException {
        if (writer == null) {
            throw new IOException("Writer not initialized");
        }

        StringBuilder sb = new StringBuilder(entries.size() * 100); // ~100 chars per line
        for (TestEntry entry : entries) {
            sb.append(entry.iteration).append(',')
                    .append(entry.result.getExecutionTime()).append(',')
                    .append(csvEscape(entry.result.getDetails())).append(',')
                    .append(entry.intervalStartMs).append(',')
                    .append(entry.intervalDurationMs).append(',')
                    .append(entry.cumulativeTimeMs).append('\n');
        }
        writer.write(sb.toString());
    }

    /**
     * Zapisuje nagłówek CSV.
     */
    private void writeHeader() throws IOException {
        if (writer == null) {
            return;
        }
        writer.write("iteration,executionTimeMs,details,intervalStartMs,intervalDurationMs,cumulativeTimeMs\n");
    }

    /**
     * Zapisuje checkpoint z bieżącym stanem bufora (na wypadek błędu).
     */
    public void saveCheckpoint() {
        lock.lock();
        try {
            saveCheckpointInternal(buffer);
        } finally {
            lock.unlock();
        }
    }

    private void saveCheckpointInternal(List<TestEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        try (BufferedWriter cpWriter = new BufferedWriter(new FileWriter(checkpointFile, true))) {
            for (TestEntry entry : entries) {
                cpWriter.write(entry.iteration + "," +
                        entry.result.getExecutionTime() + "," +
                        csvEscape(entry.result.getDetails()) + "," +
                        entry.intervalStartMs + "," +
                        entry.intervalDurationMs + "," +
                        entry.cumulativeTimeMs + "\n");
            }
            Log.i(TAG, "Saved checkpoint with " + entries.size() + " entries");
        } catch (IOException e) {
            Log.e(TAG, "Failed to save checkpoint: " + e.getMessage(), e);
        }
    }

    /**
     * Escapuje wartość dla formatu CSV.
     */
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.contains(",") || value.contains("\"") ||
                value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? ("\"" + escaped + "\"") : escaped;
    }

    /**
     * Zwraca liczbę zapisanych rekordów na dysk.
     */
    public int getTotalWritten() {
        return totalWritten.get();
    }

    /**
     * Zwraca liczbę rekordów w buforze (jeszcze niezapisanych).
     */
    public int getBufferedCount() {
        lock.lock();
        try {
            return buffer.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Zwraca ostatni błąd (null jeśli brak).
     */
    public Throwable getLastError() {
        return lastError;
    }

    /**
     * Sprawdza czy wystąpił błąd.
     */
    public boolean hasError() {
        return lastError != null;
    }

    /**
     * Zamyka writer, zapisując pozostałe dane z bufora.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;

            // Zatrzymaj executor i poczekaj na zakończenie
            writeExecutor.shutdown();
            try {
                if (!writeExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    writeExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                writeExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Zapisz pozostałe dane z bufora
            if (!buffer.isEmpty()) {
                try {
                    flushBufferInternal();
                } catch (IOException e) {
                    Log.e(TAG, "Error flushing on close: " + e.getMessage(), e);
                    saveCheckpointInternal(buffer);
                }
            }

            // Zamknij writer
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing writer: " + e.getMessage(), e);
                }
                writer = null;
            }

            Log.i(TAG, "Closed CSV writer. Total written: " + totalWritten.get() + " records");

            // Usuń checkpoint jeśli wszystko OK
            if (lastError == null && checkpointFile.exists()) {
                if (!checkpointFile.delete()) {
                    Log.w(TAG, "Could not delete checkpoint file");
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
