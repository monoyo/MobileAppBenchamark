import { router } from 'expo-router';
import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { useLocationPermission } from './hooks/useLocationPermission';

// Index now delegates to Benchmark screen directly (avoid duplicate implementations)
import Benchmark from './Benchmark';
export default Benchmark;
