# Mobile Benchmark App (Flutter)

Aplikacja Flutter (Android/iOS/desktop możliwe) do syntetycznych testów wydajności: CPU, RAM, UI (animacje), ładowanie obrazów z sieci, API request, pozyskanie lokalizacji + orkiestracja wielu iteracji i eksport wyników do CSV. Od bieżącej wersji mierzy także czas startu aplikacji (App Launch Time) oraz prezentuje średnie czasy wykonania dla każdego testu po zakończeniu całej serii.

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
- [10. Ograniczenia](#10-ograniczenia)
- [11. Szybki start](#11-szybki-start)
- [12. App Launch Time](#12-app-launch-time)
- [13. Licencja / Autor](#13-licencja--autor)

## 1. Cel
Zebranie danych dotyczących wydajności technologii Flutter. Metryka podstawowa: czas wykonania (ms).

## 2. Wymagania
- Flutter SDK (Dart >= 3.7.2 – zgodnie z `pubspec.yaml`)
- Android Studio / Xcode (dla odpowiednich platform)
- Android: urządzenie/emulator z Internetem
- Dostęp do Internetu (API + obrazy)
- Uprawnienia lokalizacji (runtime + manifest)

## 3. Technologie / Biblioteki (Flutter)
- Flutter (Material)
- `dart:isolate` – równoległość CPU
- `http` – żądanie API
- `geolocator` – lokalizacja / permission handling
- `cached_network_image` + `flutter_cache_manager` – ładowanie i cache obrazów (w testach ograniczane nagłówkami no-cache)
- `path_provider` – ścieżki do katalogów eksportu
- JSON: `dart:convert` + proste modele

## 4. Struktura katalogów / plików
`lib/`
- `main.dart` – punkt startowy: natychmiastowy pomiar startu + przejście do `BenchmarkSuitePage` (brak osobnej strony Home)
- `benchmark_suite.dart` – orkiestracja testów, progres, wyświetlanie średnich, eksport CSV
- `models/` (`test_result.dart`, inne modele) – dane
- `cpu_test.dart` – CPU benchmark (Isolates)
- `ram_test.dart` + `ram_test_impl.dart` – operacje pamięciowe
- `ui_test.dart` – animacje
- `image_loading_test.dart` – pobieranie obrazów
- `api_test.dart` + ewentualne serwisy – pobieranie JSON
- `location_test.dart` – pozycja GPS
`assets/users.json` – dane wejściowe dla RAM testu.

## 5. Orkiestracja (BenchmarkSuitePage)
- Kolejność testów: UI → CPU → RAM → Image → API → Location.
- `_iterationsPerTest = 10` → łącznie 60 przebiegów (przy 6 testach).
- Wyświetlany na górze tekst statusu; na początku: `App launched in: Xms.\nReady to start tests`.
- Start wszystkich testów przyciskiem `Start Tests`.
- Każdy test otwierany przez `Navigator.push` i zwraca `TestResult`.
- Postęp globalny = (ukończone iteracje / (liczba testów * iteracje na test)).
- Po zakończeniu: komunikat `All tests completed!` + lista średnich per test (ms) obliczonych jako średnia arytmetyczna `executionTimeMs` z zebranych iteracji.

## 6. Testy
### 6.1 CPU Test
- Isolates; pętla FP (sin, cos, sqrt); sumowanie iteracji; checksum zapobiega nieprzewidzianej optymalizacji.
- Wynik: liczba iteracji, wątki, checksum.

### 6.2 RAM Test
- `RUNS = 95 000` iteracji operacji na listach, sort, filtracja, uppercase, serializacja.
- Cel: presja alokacji i GC.

### 6.3 UI Test
- ~1500 animowanych elementów przez kilka sekund; obciążenie pipeline renderowania.

### 6.4 Image Loading Test
- Sekwencyjne pobieranie obrazów (Picsum); liczenie sukcesów i porażek.

### 6.5 API Test
- GET → JSON → parse → wynik z liczbą rekordów.

### 6.6 Location Test
- Permission + pojedynczy odczyt pozycji.

## 7. Złożoność obliczeniowa / pamięciowa
| Test | Czasowa | Pamięć | Uwagi |
|------|---------|--------|-------|
| CPU | O(T * I) | O(T) | T=liczba izolowanych zadań; I=iteracje do deadlinu |
| RAM | O(R * n log n) | O(n + mapy) | R=RUNS; n=liczba użytkowników |
| UI | O(k) | O(k) | k=liczba animowanych elementów |
| Image | O(k) | Bitmapy/cache | k=liczba obrazów |
| API | O(1) | Niska | pojedynczy request |
| Location | O(1) | Minimalna | pojedynczy odczyt |

## 8. Format wyniku / model danych
`TestResult(testName: String, executionTimeMs: int, details: String, success: bool)`

Po zakończeniu całej sesji średnie dla każdego testu są liczone na żywo w UI (nie są zapisywane do CSV – CSV zawiera surowe iteracje). Można łatwo dodać agregaty do pliku w przyszłości.

Przykład sekcji CSV:
```
# CPU Test
iteration,executionTimeMs,details
1,3012,"threads=4, iterations=123456"
...
```

## 9. Eksport wyników (CSV)
- Plik per test: `<nazwa_testu>.<YYYYMMDD_HHMMSS>.csv`.
- Android: `Android/data/<package>/files/Documents/benchmarks/` + kopia fallback w wewnętrznym `Documents/benchmarks/`.
- CSV: nagłówek + surowe iteracje.

## 10. Ograniczenia
- Sieć niestabilna wpływa na Image/API test.
- Kolejność testów może wpływać (throttling, nagrzewanie CPU).

## 11. Szybki start
1. Zainstaluj Flutter SDK.
2. Pobierz zależności:
```
flutter pub get
```
3. Uruchom:
```
flutter run
```
4. Po starcie natychmiast zobaczysz ekran suite z komunikatem o czasie startu.
5. Kliknij `Start Tests`.
6. Po zakończeniu obejrzyj średnie per test + ewentualnie `Export Results`.

## 12. App Launch Time
- Czas ładowania się pierwszego widoku.
- Prezentowany jednorazowo na górze: `App launched in: Xms.` gdzie X to zmierzony czas.

## 13. Licencja / Autor
© 2025 Mobile Benchmark App (Flutter variant). Użycie zgodnie z licencjami bibliotek zewnętrznych.
