import * as Location from 'expo-location';
import React, { useEffect, useState } from 'react';
import { Text, View } from 'react-native';

type ResultCallback = (r: { executionTimeMs: number; details: string; success: boolean }) => void;

export default function LocationTestScreen({ navigation, route }: any) {
    const onResult = route?.params?.onResult as ResultCallback | undefined;
    const [status, setStatus] = useState('Initializing...');

    useEffect(() => {
        const start = Date.now();

        const runTest = async () => {
            try {
                setStatus('Requesting permissions...');
                const { status: permStatus } = await Location.requestForegroundPermissionsAsync();

                if (permStatus !== 'granted') {
                    throw new Error('Permission denied');
                }

                setStatus('Getting location...');
                const location = await Location.getCurrentPositionAsync({
                    accuracy: Location.Accuracy.High,
                });

                const elapsed = Date.now() - start;
                const details = `Lat: ${location.coords.latitude.toFixed(6)}, Lon: ${location.coords.longitude.toFixed(6)}, Accuracy: ${location.coords.accuracy?.toFixed(1) ?? 'N/A'}m`;

                if (onResult) {
                    onResult({ executionTimeMs: elapsed, details, success: true });
                }
            } catch (error: any) {
                const elapsed = Date.now() - start;
                if (onResult) {
                    onResult({ executionTimeMs: elapsed, details: error.message, success: false });
                }
            }
            navigation.goBack();
        };

        runTest();
    }, [navigation, onResult]);

    return (
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: 16 }}>
            <Text style={{ fontSize: 18 }}>Location Test</Text>
            <Text style={{ marginTop: 8, fontSize: 14 }}>{status}</Text>
        </View>
    );
}
