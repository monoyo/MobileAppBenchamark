import React from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { router } from "expo-router";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";

export default function Index() {
  const [launchMs, setLaunchMs] = React.useState<number | null>(null);
  const startRef = React.useRef(Date.now());
  const insets = useSafeAreaInsets();

  React.useEffect(() => {
    const elapsed = Date.now() - startRef.current;
    setLaunchMs(elapsed);
  }, []);

  return (
    <SafeAreaView style={[styles.container, { paddingTop: insets.top + 12 }]}> 
      <View style={[styles.content, { paddingBottom: (insets.bottom || 0) + 72 }]}> 
        <Text style={styles.title}>Welcome to the Mobile Benchmark App - React Native Edition!</Text>
        <View style={styles.launchWrap}>
          <Text>App Launch Time: {launchMs != null ? `${launchMs}ms` : 'calculating...'}</Text>
        </View>
        <Text style={styles.subtitle}>Next step: Click the button below to start the benchmark.</Text>
      </View>
      <Pressable
        accessibilityRole="button"
        onPress={() => router.push('/suite')}
        style={({ pressed }) => [
          styles.ctaFloating,
          { bottom: (insets.bottom || 0) + 16 },
          pressed && { opacity: 0.92 },
        ]}
        android_ripple={{ color: 'rgba(255,255,255,0.25)' }}
      >
        <Text style={styles.ctaText}>Run All Tests</Text>
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingHorizontal: 12, backgroundColor: '#fff', position: 'relative' },
  content: { flex: 1 },
  title: { fontSize: 24, fontWeight: '700', color: '#000' },
  launchWrap: { paddingLeft: 25, paddingBottom: 24, paddingTop: 24, alignItems: 'center' },
  subtitle: { marginBottom: 12 },
  footer: { alignItems: 'center', paddingTop: 12 },
  cta: { backgroundColor: 'rgb(76,175,80)', paddingHorizontal: 100, paddingVertical: 12, borderRadius: 24, shadowColor: '#000', shadowOpacity: 0.2, shadowRadius: 3, elevation: 2, borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(0,0,0,0.1)' },
  ctaFloating: {
    position: 'absolute',
    left: 16,
    right: 16,
    backgroundColor: 'rgb(76,175,80)',
    paddingVertical: 14,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
    elevation: 4,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(0,0,0,0.1)',
    zIndex: 10,
  },
  ctaText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});
