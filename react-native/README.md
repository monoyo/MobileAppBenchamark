# React Native Benchmark (parity with Flutter)

This folder contains a minimal React Native app skeleton that mirrors the Flutter benchmark behavior:

- 10 iterations per test (UI, CPU, RAM, Image Loading, API, Location)
- Per-test CSV display in UI and export to app-specific Documents/benchmarks
- Image Loading: 10 hardcoded URLs, sequential loading 1→10, scrolls after each, shows real images
- CPU: ~3s workload without freezing UI
- RAM: JSON/array intensive operations
- API: fetch and measure
- Location: permission + single position

## Quick start

1) Create a new RN app (or use this folder as base)
- npx react-native@latest init rnbench --version 0.74.3
- Copy contents of `react-native/src` into your RN app `src/`
- Merge `package.json` dependencies and run `npm i` or `yarn`

2) iOS/Android linking
- ios: cd ios && pod install
- android: ensure minSdk 21+, Java 11

3) Run
- npm run android
- npm run ios

## Storage
- CSV files saved to app sandbox:
  - Android: RNFS.DocumentDirectoryPath + `/benchmarks`
  - iOS: RNFS.DocumentDirectoryPath + `/benchmarks`

## Notes
- Uses: react-navigation, react-native-fs, react-native-fast-image, react-native-permissions, react-native-geolocation-service