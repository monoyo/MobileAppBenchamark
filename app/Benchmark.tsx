import React from 'react';
import { View, Text, Pressable, ActivityIndicator, StyleSheet } from 'react-native';
import { useLocationPermission } from './hooks/useLocationPermission';
import { getLaunchTime, markSuiteReady } from './utils/launchTime';
import { router } from 'expo-router';

// New unified Benchmark screen (acts as home). Shows launch time and permission state.
export default function Benchmark() {
  const { state, request, error } = useLocationPermission(true);
  const [readyMsg, setReadyMsg] = React.useState<string>('Launching...');
  const [launchMs, setLaunchMs] = React.useState<number | null>(getLaunchTime());

  // Mark suite ready after small delay (simulate settling) per requirement (500ms before ready message).
  React.useEffect(() => {
    const t = setTimeout(() => {
      markSuiteReady();
      setLaunchMs(getLaunchTime());
      setReadyMsg('Ready to test');
    }, 500);
    return () => clearTimeout(t);
  }, []);

  const canStart = state === 'granted' && readyMsg === 'Ready to test';

  return (
    <View style={styles.root}>
      <Text style={styles.launchText}>Launch: {launchMs != null ? `${launchMs} ms` : '...'} | {readyMsg}</Text>
      <View style={styles.body}>
        <Text style={styles.title}>Benchmark Suite</Text>
        <Text style={styles.permission}>Location permission: {state}</Text>
        {error && <Text style={styles.error}>Error: {error}</Text>}
        {state === 'denied' && (
          <Pressable onPress={request} style={[styles.btn, styles.retryBtn]}>
            <Text style={styles.btnText}>Grant Location Permission</Text>
          </Pressable>
        )}
        {state === 'requesting' && <ActivityIndicator style={{ marginTop: 12 }} />}
      </View>
      <View style={styles.footer}>
        <Pressable disabled={!canStart} onPress={() => router.push('/suite')} style={[styles.btn, !canStart && styles.btnDisabled]}>
          <Text style={styles.btnText}>{canStart ? 'Start Test Suite' : 'Waiting...'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#fff', padding: 20, paddingTop: 52 },
  launchText: { fontSize: 16, fontWeight: '700', marginBottom: 24 },
  body: { flex: 1 },
  title: { fontSize: 28, fontWeight: '800', marginBottom: 12 },
  permission: { fontSize: 14, color: '#444' },
  error: { color: '#dc2626', marginTop: 8 },
  footer: { paddingBottom: 48 },
  btn: { backgroundColor: '#2563eb', paddingVertical: 16, borderRadius: 14, alignItems: 'center', marginTop: 24 },
  retryBtn: { backgroundColor: '#f97316' },
  btnDisabled: { opacity: 0.5 },
  btnText: { color: '#fff', fontWeight: '700' },
});

