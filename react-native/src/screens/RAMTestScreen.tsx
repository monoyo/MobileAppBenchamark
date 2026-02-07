import React, { useEffect } from 'react';
import { Text, View } from 'react-native';

type ResultCallback = (r: { executionTimeMs: number; details: string; success: boolean }) => void;

export default function RAMTestScreen({ navigation, route }: any) {
    const onResult = route?.params?.onResult as ResultCallback | undefined;

    useEffect(() => {
        const start = Date.now();

        // Simple RAM test - allocate and manipulate data
        const arrays: number[][] = [];
        const iterations = 100;

        for (let i = 0; i < iterations; i++) {
            const arr = Array.from({ length: 10000 }, (_, j) => Math.random() * j);
            arr.sort((a, b) => a - b);
            arrays.push(arr);
        }

        const elapsed = Date.now() - start;
        const totalElements = arrays.reduce((sum, a) => sum + a.length, 0);

        if (onResult) {
            onResult({
                executionTimeMs: elapsed,
                details: `Allocated ${iterations} arrays, ${totalElements} total elements`,
                success: true,
            });
        }
        navigation.goBack();
    }, [navigation, onResult]);

    return (
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16 }}>
            <Text style={{ fontSize: 18 }}>Running RAM Test...</Text>
        </View>
    );
}
