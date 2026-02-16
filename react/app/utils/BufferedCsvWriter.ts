import RNFS from 'react-native-fs';



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

    async initialize(header: string) {
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

    /**
     * Writes a row to the CSV file.
     * @param values List of values to write. They will be joined by commas.
     */
    async write(values: (string | number | boolean | null | undefined)[]) {
        const line = values.map(v => {
            if (v === null || v === undefined) return '';
            return this.csvEscape(String(v));
        }).join(',');

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

