# Mobile Benchmark App (Flutter)

Aplikacja Flutter (Android/iOS/desktop możliwe) do syntetycznych testów wydajności: CPU, RAM, UI (animacje), ładowanie obrazów z sieci, API request, pozyskanie lokalizacji + orkiestracja wielu iteracji i eksport wyników do CSV.

## Spis treści
- [1. Cel](#1-cel)
- [2. Wymagania](#2-wymagania)
- [3. Technologie / Biblioteki (Flutter)](#3-technologie--biblioteki-flutter)
- [4. Struktura katalogów / plików](#4-struktura-katalogów--plików)
- [5. Orkiestracja (BenchmarkSuitePage)](#5-orkiestracja-benchmarksuitepage)
- [6. Testy](#6-testy)
  - [6.1 CPU Test](#61-cpu-test)
  - [6.2 RAM Test](#62-ram-test)
  - [6.3 UI Test](#63-ui-test)
  - [6.4 Image Loading Test](#64-image-loading-test)
  - [6.5 API Test](#65-api-test)
  - [6.6 Location Test](#66-location-test)
- [7. Złożoność obliczeniowa / pamięciowa](#7-złożoność-obliczeniowa--pamięciowa)
- [8. Format wyniku / model danych](#8-format-wyniku--model-danych)
- [9. Eksport wyników (CSV)](#9-eksport-wyników-csv)
- [10. Konfiguracja uprawnień / platform](#10-konfiguracja-uprawnień--platform)
- [11. Różnice vs wariant natywny (Java)](#11-różnice-vs-wariant-natywny-java)
- [12. Ograniczenia](#12-ograniczenia)
- [13. Możliwe rozszerzenia](#13-możliwe-rozszerzenia)
- [14. Szybki start](#14-szybki-start)
- [15. Diagnostyka / wskazówki](#15-diagnostyka--wskazówki)
- [16. Licencja / Autor](#16-licencja--autor)

## 1. Cel
Porównywanie urządzeń i wersji aplikacji, wychwytywanie regresji wydajności, obserwacja wpływu bibliotek. Główna metryka: czas wykonywania (ms) + dodatkowe pola (np. liczba iteracji CPU, sukcesy/por. dla obrazów).

## 2. Wymagania
- Flutter SDK (Dart >= 3.7.2 – zgodnie z `pubspec.yaml`)
- Android Studio / Xcode (dla odpowiednich platform)
- Android: compileSdk automatycznie z Flutter; urządzenie/emulator z dostępem do Internetu
- iOS: Xcode + skonfigurowane uprawnienia lokalizacji
- Dostęp do Internetu (API + obrazy)
- Uprawnienia lokalizacji (runtime + manifest / Info.plist)

## 3. Technologie / Biblioteki (Flutter)
- Flutter (Material)
- `dart:isolate` – równoległość CPU
- `http` – żądanie API
- `geolocator` – lokalizacja / permission handling
- `cached_network_image` + `flutter_cache_manager` – ładowanie i cache obrazów (z wyłączeniem cache przez nagłówki w testach)
- `path_provider` – ścieżki do katalogów eksportu
- JSON: manualny `dart:convert` (bez generatora w tym kodzie runtime) + własne modele

## 4. Struktura katalogów / plików
`lib/`
- `main.dart` – punkt startowy (nawigacja do BenchmarkSuitePage)
- `benchmark_suite.dart` – logika orkiestracji testów i eksportu
- `models/` (`test_result.dart`, `user.dart`, `Post.dart`) – modele danych
- `cpu_test.dart` – CPU benchmark (Isolates)
- `ram_test.dart` + `ram_test_impl.dart` – intensywne operacje pamięciowe
- `ui_test.dart` – animacje wielu elementów (pipeline UI)
- `image_loading_test.dart` – ładowanie obrazów sekwencyjnie z mierzeniem powodzeń
- `api_test.dart` + `services/api_service.dart` – pobieranie postów JSON
- `location_test.dart` – pozyskanie współrzędnych

`assets/users.json` – źródło danych dla RAM testu (lista użytkowników).

## 5. Orkiestracja (BenchmarkSuitePage)
- Lista testów (kolejność): UI → CPU → RAM → Image → API → Location.
- `_iterationsPerTest = 10` (łącznie 60 przebiegów przy 6 testach).
- Każdy test uruchamiany przez `Navigator.push` i zwraca `TestResult` na `pop`.
- Postęp obliczany = (aktualny przebieg globalnie / łączna liczba) i pokazany w `LinearProgressIndicator`.
- Wyniki buforowane w mapie: nazwa testu → lista iteracji.
- Po zakończeniu wszystkich testów możliwy eksport do CSV (po jednym pliku na test).

## 6. Testy
### 6.1 CPU Test
- Funkcja `_runCpuBenchmarkParallel(durationMs)` uruchamia N izolowanych zadań (Isolate) – N = domyślnie 4 (brak natywnego API do realnych rdzeni w Flutter; przyjęty stały fallback).
- Każdy isolate: pętla do upłynięcia deadlinu; operacje `sin`, `cos`, `sqrt` + akumulacja + losowa perturbacja (`Random(seed)`).
- Zwraca: `threads, iterations (suma wszystkich), checksum` (checksum = suma akumulatorów – pozwala wykryć istotne odchylenia/logiczne błędy optymalizacji).

### 6.2 RAM Test
- `RUNS = 95 000` iteracji.
- Wczytanie listy `User` z assets (JSON) do pamięci.
- Każda iteracja: kopia i shuffle → sort po `name` → filtr (active && age>18) → uppercase nazw → wybór losowego elementu (efekt uboczny minimalny) → zliczanie imion/nazwisk do dwóch map.
- Na końcu czyszczenie dużej listy aby zmniejszyć retencję.
- Cel: presja alokacyjna (listy, kopie, stringi) + aktywowanie GC.

### 6.3 UI Test
- Generowanie 1500 animowanych kwadratów (AnimatedBuilder + AnimationController) z losowymi trajektoriami.
- Czas działania = 5 sekund (timeout), po czym wynik: „Animation frames rendered”.
- Obciąża: layout, kompozycję, GPU thread (przez ruch wielu elementów), scheduler klatek.

### 6.4 Image Loading Test
- Lista 10 URL (Picsum). Każdy ładowany kolejno w przewijanej liście; przewijanie stymulowane programowo.
- Wymuszone próby uniknięcia odczytu z cache przez nagłówki `Cache-Control: no-cache` itp.
- Watchdog: limit całkowity 60s, brak postępu >10s kończy test z oznaczeniem niepowodzeń.
- Wynik: `loaded=<n>, failed=<m>`.

### 6.5 API Test
- Pojedynczy GET do `https://jsonplaceholder.typicode.com/posts`.
- Parsowanie JSON na listę `Post`.
- Wynik: czas + liczba rekordów.

### 6.6 Location Test
- Sprawdza dostępność usług + runtime permission (Geolocator).
- Pobiera aktualną pozycję o wysokiej dokładności; wynik: czas + "lat,lon" lub status błędu (permission, disabled).

## 7. Złożoność obliczeniowa / pamięciowa
| Test | Czasowa | Pamięć | Uwagi |
|------|---------|--------|-------|
| CPU | O(T * I) | O(T) | T=liczba izolowanych zadań; I=iteracje do deadlinu (zależne od mocy CPU) |
| RAM | O(R * n log n) | O(n + mapy) | R=RUNS; n=liczba użytkowników; sort + kopiowanie + stringi |
| UI | O(k) inicjalizacja + animacja zależna od czasu | O(k) | k=1500 elementów animowanych |
| Image | O(k) | Bitmapy/cache | k=10 żądań HTTP; retry/backoff |
| API | O(1) | Niska | Jeden request + dekod JSON |
| Location | O(1) | Minimalna | Jedno pobranie pozycji |

CPU: ciężkie operacje FP + losowość utrudniają nadmierną optymalizację JIT.
RAM: generacja wielu krótkotrwałych obiektów = presja GC.

## 8. Format wyniku / model danych
`TestResult(testName: String, executionTimeMs: int, details: String, success: bool)`

Dane wewnętrznie kolekcjonowane per test, potem generowany pseudo-CSV do podglądu i osobne pliki przy eksporcie.

Przykład sekcji CSV:
```
# CPU Test
iteration,executionTimeMs,details,success
1,3012,"threads=4, iterations=123456",true
...
```

## 9. Eksport wyników (CSV)
- Jeden plik per test: `<nazwa_testu>.<YYYYMMDD_HHMMSS>.csv`.
- Android: główny katalog: `Android/data/<package>/files/Documents/benchmarks/` (app-specific external) + kopia w wewnętrznym `.../documents/benchmarks/` jako fallback.
- iOS: `Documents/benchmarks/` w sandbox aplikacji.
- Zawartość: nagłówek + wiersze iteracji (brak agregatów statystycznych poza surowymi pomiarami).

## 10. Konfiguracja uprawnień / platform
### Android
`android/app/src/main/AndroidManifest.xml` – upewnij się że zawiera (przykład):
```
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
```
Runtime permission obsługiwane przez `geolocator`.

### iOS
`ios/Runner/Info.plist` – dodaj klucze:
```
<key>NSLocationWhenInUseUsageDescription</key>
<string>Needed for location benchmark.</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>Needed for location benchmark.</string>
```
Dostęp do sieci – domyślnie dozwolony (jeśli potrzebny HTTP nie-HTTPS, wymagane App Transport Security exceptions).

## 11. Różnice vs wariant natywny (Java)
| Obszar | Flutter | Java |
|--------|---------|----------------------|
| Równoległość CPU | Isolates | Thread / join |
| UI benchmark | 1500 animowanych widgetów | Widoki + ObjectAnimator |
| Obrazy | cached_network_image + nagłówki no-cache | Glide |
| Serializacja RAM | `dart:convert` + własne modele | Gson |
| API | `http` package | Retrofit + OkHttp |
| Lokalizacja | geolocator plugin | FusedLocationProviderClient |
| Eksport CSV | path_provider + File IO (Dart) | File API Android + external storage |
| Liczba iteracji domyślna | 10 / test | 30 / test |
| Dostęp do rdzeni | Stała (4) – brak API | `Runtime.getRuntime().availableProcessors()` |

## 12. Ograniczenia
- Brak dynamicznego wykrycia realnej liczby rdzeni CPU.
- Brak metryk statystycznych (percentyle, odchylenie standardowe).
- UI test ograniczony do 5s – nie mierzy FPS bezpośrednio (tylko czas całkowity).
- Image test zależny od warunków sieci – brak serwera kontrolowanego.
- API test = publiczny endpoint (możliwe zmiany / throttling).
- RAM test może trwać długo na słabszych urządzeniach (95k iteracji).

## 14. Szybki start
1. Zainstaluj Flutter SDK.
2. Pobierz zależności:
```
flutter pub get
```
3. Uruchom na emulatorze / urządzeniu:
```
flutter run
```
4. Na ekranie głównym wybierz `Start Tests`.
5. Po zakończeniu wszystkich iteracji użyj `Export Results` aby zapisać pliki CSV.

## 15. Diagnostyka / wskazówki
- Jeśli eksport zwraca komunikat o braku dostępu: sprawdź uprawnienia do pamięci (Android 13+ – zwykle nie wymagane dla app-specific directories).
- Lokalizacja nie działa: upewnij się że GPS włączony oraz przyznano permission w systemie.
- Mała liczba iteracji CPU: mocniejszy CPU wykonuje więcej iteracji w zadanym czasie → to oczekiwane.
- Duże czasy RAM: profiluj GC (DevTools → Memory) dla lepszego wglądu.
- Watchdog w Image test może zakończyć przedwcześnie gdy brak postępu – sprawdź łączność.

## 16. Licencja / Autor
© 2025 Mobile Benchmark App (Flutter variant). Użycie zgodnie z licencjami bibliotek zewnętrznych.
