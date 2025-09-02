import { Stack } from "expo-router";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { enableScreens } from 'react-native-screens';

export default function RootLayout() {
  enableScreens(true);
  return (
    <SafeAreaProvider>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="suite" />
        <Stack.Screen name="ui-test" />
        <Stack.Screen name="cpu-test" />
        <Stack.Screen name="ram-test" />
        <Stack.Screen name="image-test" />
        <Stack.Screen name="api-test" />
        <Stack.Screen name="location-test" />
      </Stack>
    </SafeAreaProvider>
  );
}
