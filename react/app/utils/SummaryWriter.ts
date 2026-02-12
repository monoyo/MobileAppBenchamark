import RNFS from 'react-native-fs';
import { aggregatePerTest, type TestResult, type AggregatedStats } from '../types';

const CSV_HEADER = 'platform,test_name,samples,min_ms,max_ms,avg_ms,median_ms,std_ms,p25_ms,p75_ms,p95_ms,p99_ms,successes,failures';

/**
 * Convert AggregatedStats to CSV line
 */
function statsToCsvLine(stats: AggregatedStats, platform: string): string {
    return `${platform},${stats.testName},${stats.samples},${stats.minMs},${stats.maxMs},${stats.avgMs.toFixed(2)},${stats.medianMs.toFixed(2)},${stats.stdMs.toFixed(2)},${stats.p25Ms},${stats.p75Ms},${stats.p95Ms},${stats.p99Ms},${stats.successes},${stats.failures}`;
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
        const stats = aggregatePerTest(results);
        const lines = [CSV_HEADER, ...stats.map(s => statsToCsvLine(s, platform))];
        const csv = lines.join('\n') + '\n';

        const filePath = `${outputDir}/summary.csv`;
        await RNFS.writeFile(filePath, csv, 'utf8');
        return filePath;
    } catch (e) {
        console.error('Failed to export summary:', e);
        return null;
    }
}
