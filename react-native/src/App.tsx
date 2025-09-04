import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import CPUTestScreen from './screens/CPUTestScreen';
import HomeScreen from './screens/HomeScreen';
import ImageLoadingTestScreen from './screens/ImageLoadingTestScreen';
import UITestScreen from './screens/UITestScreen';
import RAMTestScreen from './screens/RAMTestScreen';
import APITestScreen from './screens/APITestScreen';
import LocationTestScreen from './screens/LocationTestScreen';

const Stack = createNativeStackNavigator();

// Punkt startu (analog do Flutter main.dart startMs)
const appStartEpochMs = Date.now();

export default function App() {
  const [launchMs, setLaunchMs] = useState<number | null>(null);

  useEffect(() => {
    // requestAnimationFrame zapewnia że pierwsza klatka UI została wyrenderowana
    requestAnimationFrame(() => {
      const diff = Date.now() - appStartEpochMs;
      setLaunchMs(diff);
    });
  }, []);

  if (launchMs === null) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <Text>Inicjalizacja...</Text>
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen name="Home" component={HomeScreen} initialParams={{ launchTimeMs: launchMs }} options={{ title: 'Benchmark Suite' }} />
        <Stack.Screen name="UI" component={UITestScreen} options={{ title: 'UI Test' }} />
        <Stack.Screen name="CPU" component={CPUTestScreen} options={{ title: 'CPU Test' }} />
        <Stack.Screen name="RAM" component={RAMTestScreen} options={{ title: 'RAM Test' }} />
        <Stack.Screen name="ImageLoading" component={ImageLoadingTestScreen} options={{ title: 'Image Loading Test' }} />
        <Stack.Screen name="API" component={APITestScreen} options={{ title: 'API Test' }} />
        <Stack.Screen name="Location" component={LocationTestScreen} options={{ title: 'Location Test' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
