import RNFS from 'react-native-fs';

const DEFAULT_HEADER = 'platform,test_name,iteration,execution_time_ms,details,interval_start_ms,interval_duration_ms,cumulative_time_ms';
const PLATFORM = 'react_native';

export class BufferedCsvWriter {
    private buffer: string[] = [];
    private filePath: string;
    private bufferSize: number;
    private testName: string;
    private initialized = false;

    constructor(filePath: string, bufferSize: number = 1000, testName: string = 'unknown') {
        this.filePath = filePath;
        this.bufferSize = bufferSize;
        this.testName = testName;
    }

    getPath(): string {
        return this.filePath;
    }

    async initialize(header: string = DEFAULT_HEADER) {
        if (this.initialized) return;
        try {
            // Verify path is writable. usually RNFS.DocumentDirectoryPath is used.
            const dir = this.filePath.substring(0, this.filePath.lastIndexOf('/'));
            await RNFS.mkdir(dir);
            await RNFS.writeFile(this.filePath, header + '\n', 'utf8');
            this.initialized = true;
        } catch (e) {
            console.error('Failed to init CSV writer', e);
        }
    }

    private csvEscape(value: string): string {
        const escaped = value.replace(/"/g, '""');
        const needsQuote = escaped.includes(',') || escaped.includes('\n') || escaped.includes('"');
        return needsQuote ? `"${escaped}"` : escaped;
    }

    async write(
        iteration: number,
        executionTimeMs: number,
        details: string,
        intervalStartMs: number = 0,
        intervalDurationMs: number = 0,
        cumulativeTimeMs: number = 0
    ) {
        const safeDetails = this.csvEscape(details);
        const safeTestName = this.csvEscape(this.testName);
        const line = `${PLATFORM},${safeTestName},${iteration},${executionTimeMs},${safeDetails},${intervalStartMs},${intervalDurationMs},${cumulativeTimeMs}`;
        this.buffer.push(line);

        if (this.buffer.length >= this.bufferSize) {
            await this.flush();
        }
    }

    async flush() {
        if (this.buffer.length === 0) return;

        try {
            const chunk = this.buffer.join('\n') + '\n';
            this.buffer = [];
            await RNFS.appendFile(this.filePath, chunk, 'utf8');
        } catch (e) {
            console.error('Flush error', e);
            // Don't clear buffer on error to retry? Or drop to avoid OOM?
            // For now, we drop to avoid getting stuck, but log error.
        }
    }

    async close() {
        await this.flush();
    }
}

