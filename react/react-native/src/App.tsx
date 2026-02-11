import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import CpuTest from './screens/CpuTest';
import HomeScreen from './screens/HomeScreen';
import ImageLoadingTest from './screens/ImageLoadingTest';
import UiTest from './screens/UiTest';
import RamTest from './screens/RamTest';
import ApiTest from './screens/ApiTest';
import LocationTest from './screens/LocationTest';

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
        <Stack.Screen name="UI" component={UiTest} options={{ title: 'UI Test' }} />
        <Stack.Screen name="CPU" component={CpuTest} options={{ title: 'CPU Test' }} />
        <Stack.Screen name="RAM" component={RamTest} options={{ title: 'RAM Test' }} />
        <Stack.Screen name="ImageLoading" component={ImageLoadingTest} options={{ title: 'Image Loading Test' }} />
        <Stack.Screen name="API" component={ApiTest} options={{ title: 'API Test' }} />
        <Stack.Screen name="Location" component={LocationTest} options={{ title: 'Location Test' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
