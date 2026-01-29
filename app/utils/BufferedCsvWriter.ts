import RNFS from 'react-native-fs';

export class BufferedCsvWriter {
    private buffer: string[] = [];
    private filePath: string;
    private bufferSize: number;
    private initialized = false;

    constructor(filePath: string, bufferSize: number = 1000) {
        this.filePath = filePath;
        this.bufferSize = bufferSize;
    }

    async initialize(header: string = 'iteration,executionTimeMs,details,intervalStartMs,intervalDurationMs,cumulativeTimeMs') {
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

    async write(
        iteration: number,
        executionTimeMs: number,
        details: string,
        intervalStartMs: number,
        intervalDurationMs: number,
        cumulativeTimeMs: number
    ) {
        const escapedDetails = details.replace(/"/g, '""');
        const needsQuote = escapedDetails.includes(',') || escapedDetails.includes('\n');
        const finalDetails = needsQuote ? `"${escapedDetails}"` : escapedDetails;

        const line = `${iteration},${executionTimeMs},${finalDetails},${intervalStartMs},${intervalDurationMs},${cumulativeTimeMs}`;
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
