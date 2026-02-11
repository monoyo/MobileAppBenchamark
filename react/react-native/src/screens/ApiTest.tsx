import React, { useEffect, useState } from 'react';
import { Text, View } from 'react-native';

type ResultCallback = (r: { executionTimeMs: number; details: string; success: boolean }) => void;

export default function ApiTest({ navigation, route }: any) {
    const onResult = route?.params?.onResult as ResultCallback | undefined;
    const [status, setStatus] = useState('Fetching...');

    useEffect(() => {
        const start = Date.now();

        fetch('https://jsonplaceholder.typicode.com/posts')
            .then(response => response.json())
            .then(data => {
                const elapsed = Date.now() - start;
                if (onResult) {
                    onResult({
                        executionTimeMs: elapsed,
                        details: `Fetched ${data.length} posts`,
                        success: true,
                    });
                }
                navigation.goBack();
            })
            .catch(error => {
                const elapsed = Date.now() - start;
                if (onResult) {
                    onResult({
                        executionTimeMs: elapsed,
                        details: `Error: ${error.message}`,
                        success: false,
                    });
                }
                navigation.goBack();
            });
    }, [navigation, onResult]);

    return (
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16 }}>
            <Text style={{ fontSize: 18 }}>{status}</Text>
            <Text style={{ marginTop: 8, fontSize: 12, opacity: 0.7 }}>Fetching from API</Text>
        </View>
    );
}
