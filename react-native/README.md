# Mobile Benchmark App (React Native variant)

Aplikacja React Native (TypeScript) odwzorowująca scenariusze benchmarków (parytet z Flutter/Java): testy CPU, RAM, UI (animacje 1500 elementów), ładowanie obrazów, API, lokalizacja + orkiestracja wielu iteracji, eksport wyników (CSV) i pomiar czasu startu aplikacji.

## Spis treści
- [1. Cel](#1-cel)
- [2. Wymagania](#2-wymagania)
- [3. Technologie / Biblioteki](#3-technologie--biblioteki)
- [4. Struktura katalogów](#4-struktura-katalogów)
- [5. Orkiestracja (HomeScreen)](#5-orkiestracja-homescreen)
- [6. Testy](#6-testy)
  - [6.1 UI Test](#61-ui-test)
  - [6.2 CPU Test](#62-cpu-test)
  - [6.3 RAM Test](#63-ram-test)
  - [6.4 Image Loading Test](#64-image-loading-test)
  - [6.5 API Test](#65-api-test)
  - [6.6 Location Test](#66-location-test)
- [7. Złożoność / charakterystyka](#7-złożoność--charakterystyka)
- [8. Model danych / format wyniku](#8-model-danych--format-wyniku)
- [9. Eksport (CSV)](#9-eksport-csv)
- [10. App Launch Time](#10-app-launch-time)
- [11. Ograniczenia](#11-ograniczenia)
- [12. Szybki start](#12-szybki-start)
- [13. Sugerowane rozszerzenia](#13-sugerowane-rozszerzenia)
- [14. Licencja / Autor](#14-licencja--autor)

## 1. Cel
Porównawcze pomiary wydajności środowiska React Native (JS/Hermes + bridge) w typowych syntetycznych scenariuszach: CPU, operacje na strukturach danych (RAM), render i animacje (UI), I/O sieciowe (obrazy + API), usługi systemowe (lokalizacja). Główna metryka: czas wykonania (ms) per iteracja testu.

## 2. Wymagania
- Node.js 18+ (LTS) / Yarn lub npm
- JDK 17 (Android build)
- Android SDK (minSdk zależny od konfiguracji RN 0.76, domyślnie 21+)
- Xcode (dla iOS) / CocoaPods (`pod install`)
- Uprawnienia lokalizacji (system, runtime)
- Dostęp do Internetu (obrazy / API)

## 3. Technologie / Biblioteki
- React Native 0.76.x + React 18.x
- `@react-navigation/native` + `@react-navigation/native-stack` – nawigacja
- `expo-file-system` – zapis plików CSV (działa też w bare RN przez Expo modules)
- `react-native-permissions` – obsługa żądań uprawnień lokalizacji
- `react-native-geolocation-service` – pozyskanie pozycji (dokładność wysoka)
- Wbudowane `Animated` (UI test) – loop 1500 elementów (useNativeDriver)
- `fetch` (API test) – JSONPlaceholder
- Własne moduły: `lib/export.ts` (tworzenie katalogów i zapis), `lib/csv.ts` (formatowanie), ekrany testowe w `screens/`

## 4. Struktura katalogów
`src/`
- `App.tsx` – pomiar launch time + rejestracja stack navigator
- `screens/HomeScreen.tsx` – orkiestracja / agregacja wyników / eksport
- `screens/CPUTestScreen.tsx`
- `screens/RAMTestScreen.tsx`
- `screens/UITestScreen.tsx`
- `screens/ImageLoadingTestScreen.tsx`
- `screens/APITestScreen.tsx`
- `screens/LocationTestScreen.tsx`
- `services/api.ts` – `fetchPosts()`
- `lib/` (csv.ts, export.ts, testTypes.ts)
- `assets/users.json` – dane wejściowe RAM testu

## 5. Orkiestracja (HomeScreen)
- Kolejność testów: UI → CPU → RAM → Image → API → Location (parytet z Flutter `benchmark_suite.dart`).
- `ITERATIONS_PER_TEST = 2` (łatwo zwiększyć – sekcja 13).
- Mechanizm: po zakończeniu testu ekran testowy wywołuje callback `onResult`, wraca `navigation.goBack()`, orkiestrator odpala kolejną iterację lub następny test.
- Pasek postępu: (aktualnie zakończone iteracje) / (tests * iterations).
- Po zakończeniu wszystkich testów obliczane średnie (blok `# Averages (ms)`).
- Wyniki trzymane w pamięci w strukturze: `Map<testName, Entry[]>` (Entry = iteration + TestResult).

## 6. Testy
### 6.1 UI Test
- 1500 kwadratów generowanych przy starcie (pozycje, kolory, wektory ruchu) – responsywnie do wymiarów ekranu i PixelRatio.
- Dwie animacje (X, Y) na każdym – sekwencja 0→1→0 (loop) przy pomocy `Animated.timing` + `Animated.loop` (useNativeDriver=true).
- Czas testu: 5 s → rejestracja wyniku (Animation frames rendered).

### 6.2 CPU Test
- Czas docelowy: ~3000 ms (deadline). 
- Pętla chunkowana (`setTimeout(0)`) – operacje FP (sin, cos, sqrt) + akumulacja checksum, liczenie iterations.
- Brak równoległości (threads=1) – JS single thread; wynik zawiera `iterations` i `checksum` (zapobiega optymalizacji dead code).

### 6.3 RAM Test
- RUNS = 95 000 iteracji (limit czasu 10 s → time-capping na wolnych urządzeniach).
- W każdej iteracji: kopie / shuffle listy użytkowników, sortowanie, filtr, uppercase, liczniki imion/nazwisk, utrzymywanie ograniczonego `bigList` (MAX_LIST=200k) by symulować presję alokacyjną i GC.
- Wynik: czas + informacja czy zakończono przez limit czasu.

### 6.4 Image Loading Test
- 10 obrazów (Picsum) ładowanych sekwencyjnie (aktywny indeks). 
- Watchdog: 60 s całość, 10 s bez progresu (oznacza zakończenie z aktualnymi wartościami – `(watchdog)`).
- Retries per obraz (MAX_RETRIES=2) z prostym backoffem. 
- Wynik: `loaded=X, failed=Y`.

### 6.5 API Test
- `fetchPosts()` → GET JSONPlaceholder.
- Pomiar czasu `request→response`; wynik: liczba postów lub błąd (executionTimeMs = -1 przy porażce).

### 6.6 Location Test
- Sprawdzenie usług lokalizacji -> uprawnienia (`react-native-permissions`) -> `react-native-geolocation-service.getCurrentPosition` (high accuracy).
- Wynik: `lat,lon` lub błąd/odmowa (`executionTimeMs = -1`).

## 7. Złożoność / charakterystyka
| Test | Złożoność czasowa | Pamięć | Uwagi |
|------|-------------------|--------|-------|
| UI | O(k) inicjalizacja, animacje w natywnym driverze | O(k) | k=1500 views (GPU + layout) |
| CPU | O(I) | O(1) | I = liczba iteracji do deadlinu (zależna od wydajności CPU) |
| RAM | O(R * n log n) | O(n + bigList) | Sort + wielokrotne kopiowanie, R=RUNS (cap czasowy) |
| Image | ~O(k) | Bitmapy/cache | k=10, sekwencyjne ładowanie |
| API | O(1) | Niskie | Pojedynczy request |
| Location | O(1) (sieć/GNSS zależna) | Minimalna | Zależne od dostępu do sensorów |

## 8. Model danych / format wyniku
`TestResult { iteration: number; executionTimeMs: number; details: string; success: boolean }`

- Iteracje przechowywane per test. Averages generowane po zakończeniu całości.

## 9. Eksport (CSV)
- Pliki per test w katalogu: `documentDirectory/benchmarks/run-<runId>-<timestamp>/<test_name>.csv`.
- Format CSV:
  - Nagłówek: `iteration,executionTimeMs,details,success`
  - Wartości escapowane (cudzysłowy dla pól z przecinkiem/nową linią).
- Wersja React Native (bare + Expo modules) korzysta z `expo-file-system` (sandbox aplikacji). 
- Na Androidzie: ścieżka wewnętrzna (wymagany dostęp ADB do odczytu / log export). Dalsze rozszerzenie: zapis do External Storage (wymaga dodatkowych uprawnień WRITE/READ + migracja modułu FS).

## 10. App Launch Time
- `App.tsx` mierzy czas od startu JS (Date.now() na module) do pierwszego `requestAnimationFrame` po zamontowaniu – wynik w ms przekazywany do Home jako `launchTimeMs` i wyświetlany w nagłówku.

## 11. Ograniczenia
- Brak równoległych workerów/JSI (CPU test jednowątkowy) – trudniej porównywać z natywnym multi-thread.
- RAM test ogranicza czas (MAX_MS) – porównania między urządzeniami mogą mieć różną liczbę realnych iteracji (warto logować iterations, obecnie brak w szczegółach – dołączenie zalecane).
- Brak pomiaru zużycia pamięci (wymaga natywnych modułów / profilera). 
- Watchdog w Image test kończy całość, ale wynik `success=true` – można rozróżnić stan awaryjny flagą.
- Eksport tylko do sandboxu (brak share picker / intent send).
- Brak agregatów typu min / max / p95 (tylko średnia) – do rozbudowy.

## 12. Szybki start
1. Instalacja zależności:
```
npm install
# lub
yarn install
```
2. iOS (macOS):
```
cd ios
pod install
cd ..
```
3. Uruchom:
```
npm run android
# lub
yarn android
npm run ios
# lub
yarn ios
```
4. Po starcie: na ekranie głównym wybierz "Start Tests".
5. Poczekaj na zakończenie wszystkich testów → pojawi się blok średnich i możliwość eksportu.
6. Użyj "Export Results" aby wygenerować pliki CSV (ścieżki w alertcie / logach).

## 13. Sugerowane rozszerzenia
- Dodanie liczby wykonanych iteracji i checksum do RAM testu (walidacja).
- Multi-thread CPU (np. biblioteka `react-native-multithreading` / JSI workers) – zbliżenie do isolate w Flutter.
- Eksport zbiorczy jednego pliku scalającego wszystkie testy + metadane (model urządzenia, RN version, Hermes on/off).
- P95 / min / max / median / stdev dla każdej metryki.
- Dodatkowy test I/O (seryjna / równoległa serializacja JSON + zapis plików) i test sieci równoległych requestów.
- Integracja z rejestrem wyników (remote upload) + dashboard.
- Wersja headless (CLI) uruchamiana przez `adb shell am start` i automatyczny eksport.

## 14. Licencja / Autor
© 2025 Mobile Benchmark App (React Native variant). Użycie zgodnie z licencjami bibliotek zależnych.
