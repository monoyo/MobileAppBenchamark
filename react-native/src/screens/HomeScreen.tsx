import React from 'react';
import { Button, StyleSheet, View } from 'react-native';

export default function HomeScreen({ navigation }: any) {
  return (
    <View style={styles.container}>
      <Button title="Run CPU Test" onPress={() => navigation.navigate('CPU')} />
      <View style={{ height: 12 }} />
      <Button title="Run Image Loading Test" onPress={() => navigation.navigate('ImageLoading')} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24 },
});
