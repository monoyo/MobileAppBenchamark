import React, { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import * as FileSystem from 'expo-file-system';
import { TestResult } from '../lib/testTypes';

type ResultCallback = (r: { executionTimeMs: number; details: string; success: boolean }) => void;

const TARGET_SAMPLES = 10000;

export default function ApiTest({ navigation, route }: any) {
    const onResult = route?.params?.onResult as ResultCallback | undefined;
    const [status, setStatus] = useState('Initializing...');

    useEffect(() => {
        let cancelled = false;

        const runTest = async () => {
            const startTime = Date.now();
            let successCount = 0;
            let errorCount = 0;
            const results: string[] = [];

            // Header
            results.push('iteration,elapsedTimeMs');

            for (let i = 0; i < TARGET_SAMPLES; i++) {
                if (cancelled) return;

                const loopStart = Date.now();
                try {
                    await fetch('https://jsonplaceholder.typicode.com/posts');
                    successCount++;
                } catch (e) {
                    errorCount++;
                }
                const duration = Date.now() - loopStart;

                // Buffer result
                results.push(`${i + 1},${duration}`);

                // Update UI every 100 iterations to avoid blocking
                if (i % 100 === 0) {
                    setStatus(`API Test: ${i + 1} / ${TARGET_SAMPLES}`);
                    await new Promise(r => setTimeout(r, 0));
                }
            }

            const totalTime = Date.now() - startTime;
            const details = `Executed ${TARGET_SAMPLES} calls. Success: ${successCount}, Errors: ${errorCount}`;

            // Write CSV
            try {
                const path = `${FileSystem.documentDirectory}api_test.csv`;
                await FileSystem.writeAsStringAsync(path, results.join('\n'), {
                    encoding: FileSystem.EncodingType.UTF8
                });
                console.log(`Saved API test results to ${path}`);
            } catch (e) {
                console.error('Failed to write CSV', e);
            }

            if (!cancelled && onResult) {
                onResult({
                    executionTimeMs: totalTime,
                    details,
                    success: true,
                });
                navigation.goBack();
            }
        };

        runTest();

        return () => {
            cancelled = true;
        };
    }, [navigation, onResult]);

    return (
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16 }}>
            <Text style={{ fontSize: 18 }}>{status}</Text>
            <Text style={{ marginTop: 8, fontSize: 12, opacity: 0.7 }}>Fetching from API</Text>
        </View>
    );
}
