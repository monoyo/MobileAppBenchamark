import RNFS from 'react-native-fs';
import { aggregatePerTest, type TestResult, type AggregatedStats } from '../types';

const CSV_HEADER = 'platform,test_name,samples,min_ms,max_ms,avg_ms,median_ms,std_ms,p25_ms,p75_ms,p95_ms,p99_ms,successes,failures';

/**
 * Compute standard deviation from an array of values
 */
function computeStdDev(values: number[], mean: number): number {
    if (values.length === 0) return 0;
    const variance = values.reduce((sum, v) => sum + (v - mean) * (v - mean), 0) / values.length;
    return Math.sqrt(variance);
}

/**
 * Compute percentile using nearest-rank method
 */
function percentile(sorted: number[], p: number): number {
    if (sorted.length === 0) return 0;
    const rank = Math.min(sorted.length - 1, Math.max(0, Math.ceil(p * sorted.length) - 1));
    return sorted[rank];
}

export type ExtendedStats = AggregatedStats & {
    platform: string;
    stdMs: number;
    p25Ms: number;
    p75Ms: number;
};

/**
 * Extend the existing aggregatePerTest with additional statistics
 */
export function aggregateWithExtendedStats(results: TestResult[], platform: string = 'react_native'): ExtendedStats[] {
    const byTest: Record<string, TestResult[]> = {};
    for (const r of results) {
        if (!byTest[r.testName]) byTest[r.testName] = [];
        byTest[r.testName].push(r);
    }

    return Object.entries(byTest).map(([testName, arr]) => {
        const times = arr.filter(a => a.executionTimeMs >= 0).map(a => a.executionTimeMs).sort((a, b) => a - b);
        const successes = arr.filter(a => a.success).length;
        const failures = arr.length - successes;

        const avgMs = times.length ? times.reduce((s, v) => s + v, 0) / times.length : 0;

        return {
            platform,
            testName,
            samples: arr.length,
            minMs: times[0] ?? 0,
            maxMs: times[times.length - 1] ?? 0,
            avgMs,
            medianMs: times.length > 0 ? (times.length % 2 === 0
                ? (times[times.length / 2 - 1] + times[times.length / 2]) / 2
                : times[Math.floor(times.length / 2)]) : 0,
            stdMs: computeStdDev(times, avgMs),
            p25Ms: percentile(times, 0.25),
            p75Ms: percentile(times, 0.75),
            p95Ms: percentile(times, 0.95),
            p99Ms: percentile(times, 0.99),
            successes,
            failures,
        };
    });
}

/**
 * Convert ExtendedStats to CSV line
 */
function statsToCsvLine(stats: ExtendedStats): string {
    return `${stats.platform},${stats.testName},${stats.samples},${stats.minMs},${stats.maxMs},${stats.avgMs.toFixed(2)},${stats.medianMs.toFixed(2)},${stats.stdMs.toFixed(2)},${stats.p25Ms},${stats.p75Ms},${stats.p95Ms},${stats.p99Ms},${stats.successes},${stats.failures}`;
}

/**
 * Export summary statistics to a CSV file
 */
export async function exportSummary(
    results: TestResult[],
    outputDir: string,
    platform: string = 'react_native'
): Promise<string | null> {
    try {
        const stats = aggregateWithExtendedStats(results, platform);
        const lines = [CSV_HEADER, ...stats.map(statsToCsvLine)];
        const csv = lines.join('\n') + '\n';

        const filePath = `${outputDir}/summary.csv`;
        await RNFS.writeFile(filePath, csv, 'utf8');
        return filePath;
    } catch (e) {
        console.error('Failed to export summary:', e);
        return null;
    }
}
