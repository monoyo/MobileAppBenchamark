# Mobile Benchmark App (Kotlin)

Aplikacja Android (Kotlin) do syntetycznych testów wydajności: CPU, RAM, UI, ładowanie obrazów, API, lokalizacja + orkiestracja wielu iteracji i eksport wyników do CSV.

## Spis treści
- [1. Cel](#1-cel)
- [2. Wymagania](#2-wymagania)
- [3. Technologie / Biblioteki (Kotlin wariant)](#3-technologie--biblioteki-kotlin-wariant)
- [4. Struktura pakietów (Kotlin)](#4-struktura-pakietów-kotlin)
- [5. Orkiestracja (BenchmarkSuiteActivity)](#5-orkiestracja-benchmarksuiteactivity)
- [6. Testy](#6-testy)
  - [6.1 CPU Test](#61-cpu-test)
  - [6.2 RAM Test](#62-ram-test)
  - [6.3 UI Test](#63-ui-test)
  - [6.4 Image Loading Test](#64-image-loading-test)
  - [6.5 API Test](#65-api-test)
  - [6.6 Location Test](#66-location-test)
  - [6.7 Application Init](#67-application-init)
- [7. Złożoność (dominujące czynniki)](#7-złożoność-dominujące-czynniki)
- [8. Format wyniku](#8-format-wyniku)
- [9. Eksport](#9-eksport)
- [10. Ograniczenia](#10-ograniczenia)
- [11. Szybki start](#11-szybki-start)
- [12. App Launch Time](#12-app-launch-time)
- [13. Dalszy rozwój](#13-dalszy-rozwoj)
- [14. Licencja / Autor](#14-licencja--autor)

---
## 1. Cel
Zebranie danych dotyczących wydajności technologii Kotlin. Metryka podstawowa: czas wykonania (ms).

## 2. Wymagania
- JDK 17 ( `sourceCompatibility = JavaVersion.VERSION_17` )
- Android Studio (compileSdk 35, targetSdk 35, minSdk 24)
- Gradle Wrapper + Version Catalog (`libs.*`)
- Dostęp do Internetu (API + obrazy)
- Uprawnienia lokalizacji (ACCESS_FINE_LOCATION)

## 3. Technologie / Biblioteki (Kotlin wariant)
| Biblioteka | Rola |
|-----------|------|
| AndroidX Core / AppCompat / ConstraintLayout / RecyclerView | Podstawy UI |
| Material Components | Komponenty Material |
| Kotlin Coroutines (core, android) | Współbieżność testów (CPU, API, obrazy) |
| Kotlinx Serialization JSON | Serializacja / deserializacja (RAM, API) |
| Coil | Ładowanie i dekodowanie obrazów (Image Test) |
| Ktor Client (core, android, logging, content-negotiation, serialization) | HTTP (API Test) |
| Play Services Location | Lokalizacja (Location Test) |
| Lifecycle Runtime KTX | `lifecycleScope` w aktywnościach |

Usunięte po migracji: Glide, Retrofit, OkHttp (zastąpione przez Coil + Ktor).

## 4. Struktura pakietów (Kotlin)
Pakiet `com.jossy.android.mobilebenchmarkappkotlin`
- activity/ (BenchmarkSuiteActivity, CPUTestActivity, RAMTestActivity, UITestActivity, ImageLoadingActivity, ApiTestActivity, LocationTestActivity)
- data/ (TestResult, modele domenowe, np. Post)
- service/ (ApiService – Ktor)
- CPUTest.kt / RAMTest.kt
- BenchmarkApplication.kt (pre-warm Coil, StrictMode w debug)

## 5. Orkiestracja (BenchmarkSuiteActivity)
- TEST_ITERATIONS domyślnie = 30; ALL_TESTS = 6 → 180 przebiegów.
- Testy wykonywane sekwencyjnie; po każdym wynik (TestResult) dodawany do listy.
- Po zakończeniu: agregacja + podgląd + eksport CSV (per test).

## 6. Testy
### 6.1 CPU Test
- Korutyny na `Dispatchers.Default` (liczba wątków ≈ liczbie rdzeni).
- Pętla do deadlinu (`now + durationMs`): `sin`, `cos`, `sqrt` + akumulacja.
- Wynik: liczba korutyn, czas, suma iteracji, checksum.

### 6.2 RAM Test
- RUNS = 95_000.
- Intensywne operacje na kolekcjach + serializacja / deserializacja JSON (kotlinx.serialization) → wysoka presja alokacyjna i GC.
- Kopiowanie, shuffle, sort, filtr, mapowanie, serializacja, deserializacja, agregacje.

### 6.3 UI Test
- Generowanie ~1500 widoków.
- Animacje property (obciążenie render pipeline / measure/layout/draw).

### 6.4 Image Loading Test
- 10 URL (Picsum) ładowanych Coil w RecyclerView.
- Zliczanie sukces/porażka; koniec po obsłużeniu wszystkich.

### 6.5 API Test
- Ktor GET `https://jsonplaceholder.typicode.com/posts`.
- Pomiar latency request→response + dekodowanie JSON (serialization plugin).

### 6.6 Location Test
- Fused Location Provider (Play Services) – jednorazowe pobranie aktualnej lokalizacji; pomiar czasu.

### 6.7 Application Init
- StrictMode (debug) + pre-warm `Coil.imageLoader` w tle.

## 7. Złożoność (dominujące czynniki)
| Test | Czasowa | Uwagi |
|------|---------|-------|
| CPU | O(T * I) | I = liczba iteracji FP do deadlinu |
| RAM | O(R * n log n) | sort/shuffle + JSON + wielokrotne kopie |
| UI | O(n) | n = liczba generowanych View |
| Image | O(k) | k = 10 żądań (sieć/cache zależne) |
| API | O(1) | Pojedynczy request |
| Location | O(1) | Zależne od providerów |

## 8. Format wyniku
Struktura `TestResult(testName: String, executionTimeMs: Long, details: String, isSuccessful: Boolean)`.

Przykładowy CSV:
```
iteration,executionTimeMs,details
0,512,"threads=8, iterations=1234567"
```

## 9. Eksport
- Plik per test: `<test_name>_<timestamp>.csv`.
- Katalog docelowy: `.../files/Documents/benchmarks/` (prywatne dla aplikacji, dostępne przez systemowy picker / adb pull).

## 10. Ograniczenia
- Zmienność sieci (API / Image) → jitter; brak retry (można dodać).
- Sekwencyjne uruchamianie testów: możliwe nagrzewanie CPU wpływa na późniejsze.
- Brak percentyli – raportuje tylko średnie / surowe przebiegi (możliwość rozszerzenia).
- Lokalizacja zależna od ustawień urządzenia (GPS/Wi-Fi). 

## 11. Szybki start
1. Uruchom aplikację.
2. Kliknij "Start Suite".
3. Poczekaj aż wszystkie testy (6 * 30) się zakończą.
4. Użyj opcji "Export" aby zapisać CSV.

## 12. App Launch Time
- Mierzony czas od startu procesu do wyświetlenia pierwszego ekranu.
- Wyświetlany jednorazowo: `App launched in: Xms`.

## 13. Dalszy rozwój
- Percentyle (P95 / P99) czasów.
- Rejestr pamięci (PSS) w trakcie testów.
- Macrobenchmark / Baseline Profiles (start-up, scroll, animacje).
- Lokalny mock serwer (stabilizacja API/Image testów).
- Telemetria energii (Battery Historian / Perfetto integracja).

## 14. Licencja / Autor
© 2025 Mobile Benchmark App (Kotlin variant). Użycie zgodnie z licencjami bibliotek zewnętrznych.