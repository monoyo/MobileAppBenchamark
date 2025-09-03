import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import React from 'react';
import CPUTestScreen from './screens/CPUTestScreen';
import HomeScreen from './screens/HomeScreen';
import ImageLoadingTestScreen from './screens/ImageLoadingTestScreen';

const Stack = createNativeStackNavigator();

export default function App() {
  return (
    <NavigationContainer>
      <Stack.Navigator>
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="CPU" component={CPUTestScreen} options={{ title: 'CPU Test' }} />
        <Stack.Screen name="ImageLoading" component={ImageLoadingTestScreen} options={{ title: 'Image Loading Test' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
