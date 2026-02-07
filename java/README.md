# Mobile Benchmark App (Java)

Aplikacja Android (Java) do syntetycznych testów wydajności: CPU, RAM, UI, ładowanie obrazów, API, lokalizacja, + orkiestracja wielu iteracji i eksport wyników do CSV.

## Spis treści
- [1. Cel](#1-cel)
- [2. Wymagania](#2-wymagania)
- [3. Technologie / Biblioteki (Java wariant)](#3-technologie--biblioteki-java-wariant)
- [4. Struktura pakietów (Java)](#4-struktura-pakietów-java)
- [5. Orkiestracja (BenchmarkSuiteActivity)](#5-orkiestracja-benchmarksuiteactivity)
- [6. Testy](#6-testy)
  - [6.1 CPU Test](#61-cpu-test)
  - [6.2 RAM Test](#62-ram-test)
  - [6.3 UI Test](#63-ui-test)
  - [6.4 Image Loading Test](#64-image-loading-test)
  - [6.5 API Test](#65-api-test)
  - [6.6 Location Test](#66-location-test)
  - [6.7 Application (BenchmarkApplication)](#67-application-benchmarkapplication)
- [7. Złożoność (dominujące czynniki)](#7-złożoność-dominujące-czynniki)
- [8. Format wyniku](#8-format-wyniku)
- [9. Eksport](#9-eksport)
- [10. Ograniczenia](#10-ograniczenia)
- [11. Szybki start](#11-szybki-start)
- [12. App Launch Time](#12-app-launch-time)
- [13. Licencja / Autor](#13-licencja--autor)

## 1. Cel
Zebranie danych dotyczących wydajności technologii Java. Metryka podstawowa: czas wykonania (ms).

## 2. Wymagania
- JDK 17
- Android Studio (compileSdk 35, minSdk 24, targetSdk 35)
- Dostęp do Internetu (API + obrazy)
- Uprawnienia lokalizacji (ACCESS_FINE_LOCATION)

## 3. Technologie / Biblioteki (Java wariant)
- AndroidX AppCompat / ConstraintLayout / RecyclerView / Material
- Glide – ładowanie i cache obrazów
- Retrofit 2 + OkHttp 4 + Logging Interceptor – test API
- Gson – serializacja / deserializacja (RAMTest, API)
- Play Services Location – lokalizacja
- Własne wątki (Thread) dla CPU testu (brak korutyn)

## 4. Struktura pakietów (Java)
Pakiet `com.jossy.android.mobilebenchmarkappjava`
- activity/ (BenchmarkSuiteActivity, CPUTestActivity, RAMTestActivity, UITestActivity, ImageLoadingActivity, ApiTestActivity, LocationTestActivity)
- data/ (TestResult, TestEntry, CpuResult, User, Post)
- service/ (ApiService – Retrofit interface)
- CPUTest.java / RAMTest.java / Users.java
- BenchmarkApplication.java (pre-warm Glide, zarządzanie pamięcią)

## 5. Orkiestracja (BenchmarkSuiteActivity)
- TEST_ITERATIONS = 30, ALL_TESTS = 6 → 180 przebiegów.
- Kolejno uruchamiane aktywności testowe (`startActivityForResult` legacy).
- Zbieranie `TestResult` (Serializable) → agregacja → podgląd CSV → eksport per test do `/Android/data/<pkg>/files/Documents/benchmarks/`.

## 6. Testy
### 6.1 CPU Test
- Liczba wątków = `max(1, availableProcessors())` (lub wartość podana).
- Każdy wątek: pętla do upływu limitu czasu (`deadline = now + durationMs`) z: `Math.sin`, `Math.cos`, `Math.sqrt` + akumulacja.
- Wynik: `threads, durationMs, iterations (suma), checksum`.

### 6.2 RAM Test
- RUNS = 1800.
- Wczytanie listy `User` z wbudowanego JSON (Users.list) przy użyciu Gson.
- Iteracja: kopiowanie + shuffle + sort (Comparator) + filtr (active && age>18) + uppercase imion + serializacja → deserializacja + zliczenia map imion/nazwisk.
- Wysoka presja alokacyjna i GC.

### 6.3 UI Test
- Generowanie wielu widoków + animacje property (ObjectAnimator) dla obciążenia pipeline renderowania.

### 6.4 Image Loading Test
- 10 URL (Picsum) ładowanych równolegle Glide.
- Listener sukces/porażka → inkrementacja licznika → koniec po wszystkich.

### 6.5 API Test
- Retrofit GET `https://jsonplaceholder.typicode.com/posts`.
- Mierzy latency request→response (Callback asynchroniczny OkHttp).

### 6.6 Location Test
- Fused Location Provider (Play Services) – próba pozyskania bieżącej lokalizacji; pomiar czasu.

### 6.7 Application (BenchmarkApplication)
- Pre-warm Glide w wątku tła.
- Oczyszczanie cache Glide przy `onTrimMemory` / `onLowMemory`.

## 7. Złożoność (dominujące czynniki)
| Test | Czasowa | Pamięć | Uwagi |
|------|---------|--------|-------|
| CPU | O(T * I) | O(T) | I = liczba iteracji do deadlinu (zależne od CPU) |
| RAM | O(R * n log n) | O(n + mapy) | sort + wielokrotne alokacje, JSON |
| UI | O(n) inicjalizacja | O(n) | n = liczba View/animacji |
| Image | ~O(k) | Bitmapy/cache | k=10 żądań HTTP |
| API | O(1) | Niski | Pojedynczy request |
| Location | O(1) | Minimalna | Zależne od sygnału / providerów |

Szczegóły CPU: pętla FP (sin/cos/sqrt) – operacje kosztowne, dobre do różnicowania urządzeń. 
RAM: powtarzające sortowania, tworzenie kopii list, serializacja (Gson) – miks CPU + GC.

## 8. Format wyniku
`TestResult(testName: String, executionTime: Long, details: String, isSuccessful: Boolean)`

CSV per test:
```
iteration,executionTimeMs,details
0,512,"threads=8, iterations=1234567"
...
```

## 9. Eksport
Plik per test: `<test_name>_<timestamp>.csv` w `.../files/Documents/benchmarks/`.

## 10. Ograniczenia
- Użycie `startActivityForResult` (legacy) – można zmodernizować do Activity Result API.
- Sieć niestabilna wpływa na Image/API test.
- Kolejność testów może wpływać (throttling, nagrzewanie CPU).

## 11. Szybki start
1. Uruchom aplikację.
2. Naciśnij "Start Tests".
3. Poczekaj na zakończenie wszystkich iteracji.
4. Użyj "Export" aby zapisać CSV.


## 12. App Launch Time
- Czas ładowania się pierwszego widoku.
- Prezentowany jednorazowo na górze: `App launched in: Xms.` gdzie X to zmierzony czas.


## 13. Licencja / Autor
© 2025 Mobile Benchmark App (Java variant). Użycie zgodnie z licencjami bibliotek zewnętrznych.
