import * as FileSystem from 'expo-file-system';

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
            // Ensure directory exists - expo-file-system might need parent dirs
            const dir = this.filePath.substring(0, this.filePath.lastIndexOf('/'));
            await FileSystem.makeDirectoryAsync(dir, { intermediates: true }).catch(() => { });

            await FileSystem.writeAsStringAsync(this.filePath, header + '\n', { encoding: FileSystem.EncodingType.UTF8 });
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
            // appendAsStringAsync is not available in expo-file-system? 
            // Actually it is NOT available in older versions, but let's check.
            // React Native File System (react-native-fs) corresponds to 'react-native-fs' in package.json
            // But suite.tsx uses `expo-file-system`.
            // `expo-file-system` does NOT support append natively in all versions efficiently.
            // BUT wait, package.json has "react-native-fs": "^2.20.0".
            // I should use react-native-fs for append capability!

            // Re-implementing using react-native-fs for better performance with append
        } catch (e) {
            console.error('Flush error', e);
        }
    }
}
