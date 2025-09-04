# Mobile Benchmark App (Kotlin)

Kompleksowa aplikacja Android do wykonywania powtarzalnych testów wydajności urządzenia: CPU, RAM, UI (rendering/animacje), ładowanie obrazów, API (sieć), lokalizacja (GPS / Network) oraz orkiestracja wielu iteracji i eksport wyników do CSV.

## Spis treści
1. Cel projektu
2. Wymagania środowiskowe
3. Konfiguracja / Budowanie
4. Struktura pakietów i modułów
5. Użyte biblioteki (wersje i rola)
6. Opis testów i przepływ działania (flow)
7. Analiza złożoności i charakterystyka obciążeń
8. Format wyników i eksport CSV
9. Modele danych
10. Strategia testowania / możliwe rozszerzenia testów
11. Dobre praktyki i ograniczeniax
12. Pomysły na dalszy rozwój

---
## 1. Cel projektu
Aplikacja ma umożliwić szybkie uzyskanie wielu metryk obserwowalnych (czas wykonania) dla podstawowych klas obciążeń na urządzeniu mobilnym Android. Wyniki mogą służyć do:
- Porównań między urządzeniami
- Rejestrowania regresji wydajności build-to-build
- Walidacji wpływu zmian w konfiguracji (np. wersji bibliotek)

## 2. Wymagania środowiskowe
- Android Studio Giraffe/Koala+ (dowolna wersja wspierająca AGP dla compileSdk 35)
- JDK 17 (ustawione w projekcie: `sourceCompatibility = JavaVersion.VERSION_17`)
- Gradle Wrapper (zakładany w repo – wersje pluginów utrzymywane przez Version Catalog `libs.*`)
- minSdk = 24, targetSdk = 35, compileSdk = 35
- Dostęp do Internetu (test API + ładowanie obrazów + Glide)
- Uprawnienia lokalizacji (Location Test)

## 3. Konfiguracja / Budowanie
1. Sklonuj repozytorium.
2. Otwórz w Android Studio (import projektu Gradle).
3. Zbuduj: (Gradle: `assembleDebug`).
4. Uruchom na urządzeniu fizycznym (zalecane) lub emulatorze z działającymi usługami Google (dla FusedLocationProviderClient).

Uruchomienie: po instalacji otwórz aplikację → przycisk "Start Suite" uruchomi sekwencyjnie 6 testów x 30 iteracji.

## 4. Struktura pakietów i modułów
`com.jossy.android.mobilebenchmarkappkotlin`
- `activity/` – ekran główny oraz osobne Activity dla każdego testu.
- `data/` – modele danych + wynik testu.
- `service/` – definicja interfejsu API (Retrofit).
- Pliki testów syntetycznych (CPU / RAM / Location) w katalogu głównym pakietu.

Główna orkiestracja: `BenchmarkSuiteActivity`.

## 5. Użyte biblioteki
| Biblioteka | Rola |
|-----------|------|
| AndroidX Core/AppCompat/ConstraintLayout/RecyclerView | Standardowe komponenty UI i wsparcie kompatybilności |
| Material Components | Elementy UI Material |
| Kotlin Coroutines (`kotlinx-coroutines-android`, `play-services`) | Równoległość / wątki / zawieszalne operacje |
| Kotlinx Serialization JSON (1.6.0) | Serializacja / deserializacja danych w RAMTest |
| Retrofit 2.9.0 + Gson Converter | Klient HTTP API testu sieci |
| OkHttp 4.12.0 + Logging Interceptor | Transport HTTP i logowanie |
| Glide 4.16.0 | Ładowanie i dekodowanie obrazów z sieci |
| Play Services Location 21.0.1 | Dostęp do aktualnej lokalizacji (Fused Provider) |
| MPAndroidChart (zadeklarowany) | Możliwe wizualizacje (niewykorzystane w pokazanym kodzie UI) |
| JUnit / Espresso / AndroidX Test | Podstawy testów jednostkowych i instrumentalnych |

Wersje pluginów (AGP / Kotlin) – utrzymywane poprzez aliasy `libs.plugins.*` (plik `libs.versions.toml` znajduje się wyżej w strukturze projektu – nie pokazany w module). 

## 6. Opis testów i przepływ działania (flow)
### 6.1 BenchmarkSuiteActivity
- Ustawia liczbę iteracji: 30 na każdy z 6 testów (łącznie 180 przebiegów).
- Dla każdej iteracji startuje dedykowane Activity testowe metodą `startActivityForResult`.
- Po zakończeniu testu odbiera `TestResult` (Serializable) → kolekcjonuje → aktualizuje progres → generuje dynamiczny podgląd pseudo-CSV.
- Po zakończeniu wszystkich testów wylicza średnie czasy per test.
- Eksport: przycisk generuje osobne pliki CSV dla każdego typu testu w `.../Android/data/<pkg>/files/Documents/benchmarks/`.

### 6.2 UI Test (UITestActivity)
- Tworzy 1500 kwadratowych widoków (50x50 px) o losowym kolorze i pozycji.
- Dla każdego dwóch animacji (X, Y) w pętli (ObjectAnimator, repeat = INFINITE, 2000 ms).
- Czas testu = 5 sekund (opóźnione zakończenie). Zwraca czas renderowania i inicjalizacji animacji.

### 6.3 CPU Test (CPUTestActivity + CPUTest)
- Wykorzystuje liczbę rdzeni = `availableProcessors()`.
- Każdy wątek wykonuje ciasną pętlę do upływu czasu (domyślnie 3000 ms) z operacjami: `sin`, `cos`, `sqrt`, akumulacja.
- Zwraca: łączna liczba iteracji, suma kontrolna (checksum), liczba wątków, czas.

### 6.4 RAM Test (RAMTestActivity + RAMTest)
- Stała `RUNS = 95_000` powtórzeń.
- Na wejściu wczytuje listę użytkowników z wbudowanego JSON (plik `users.kt` zawiera `jsonData`).
- W każdej iteracji: shuffle → addAll do dużej listy → sort → filter/map → serializacja i deserializacja JSON → analiza imion/nazwisk.
- Na końcu czyści dużą listę by ograniczyć zatrzymanie pamięci (GC friendly).

### 6.5 Image Loading Test (ImageLoadingActivity)
- Lista 10 URL (picsum). 
- Używa RecyclerView + Glide; każdy obraz ładowany asynchronicznie.
- Mierzy czas od rozpoczęcia do załadowania (bądź błędu) wszystkich obrazów.

### 6.6 API Test (ApiTestActivity)
- Retrofit GET `/posts` z `https://jsonplaceholder.typicode.com/`.
- Czas = latency HTTP (request → response body). 
- Prosty sukces/fail.

### 6.7 Location Test (LocationTestActivity + LocationBenchmarkTest)
- Żąda `ACCESS_FINE_LOCATION` i `ACCESS_COARSE_LOCATION`.
- Używa `FusedLocationProviderClient.getCurrentLocation(Priority.HIGH_ACCURACY)`.
- Fallback do `lastLocation` jeśli brak natychmiastowej lokalizacji.
- Mierzy czas uzyskania (elapsedRealtime delta) i zwraca parametry: lat, lon, accuracy, provider, altitude (opcjonalnie), speed, bearing.

### 6.8 Aplikacja (BenchmarkApplication)
- W trybie debug włącza StrictMode (wykrywanie IO / zasobów) + pre-warm Glide na wątku tła.

## 7. Analiza złożoności i charakterystyka obciążeń
| Test | Dominujące operacje | Czasowa | Pamięciowa | Komentarz |
|------|---------------------|---------|------------|-----------|
| UI | Tworzenie 1500 View + animacje property | O(n) dla inicjalizacji; animacje zależne od czasu (5s) | O(n) (referencje do View) | Stres na GC + UI thread + render pipeline |
| CPU | Pętla trygonometryczna per wątek do czasu deadlinu | O(T * I) (I zależne od wydajności CPU) | O(T) | I ~ liczba iteracji przed upływem czasu (pomiar intensywności) |
| RAM | 95k * (shuffle + sort + filter + serializacja) | O(R * (n log n + n)) ~ O(R * n log n) | O(n + akumulacje map) | Wymusza alokacje, GC, przetwarzanie JSON |
| Image | 10 requestów HTTP + dekodowanie obrazów | O(k) (k=liczba obrazów) sieć równoległa | Strumienie + dekodowane bitmapy (chwilowo) | Wpływ cache / CPU dekodowania | 
| API | 1 request HTTP | O(1) | Minimalna | Czysty pomiar latency |
| Location | Zapytanie o aktualną lokalizację | O(1) typowo (czas zależy od usług lokalizacyjnych) | Minimalna | Zależne od warunków sygnału / providerów |

Szczegóły matematyczne CPU Test:
- Każdy iteration: ~5 operacji zmiennoprzecinkowych (sin, cos, sqrt dominują – koszt >> add/multiply).
- Ślad obliczeniowy ≈ iteracje * (koszt trygonometryczny). Różnice między urządzeniami dobrze odwzorowane w `iterations`.

Szczegóły RAM Test:
- `shuffle`: O(n)
- `sortedBy`: O(n log n)
- `filter + map`: O(n)
- Serializacja/deserializacja: ≈ O(n)
- Całość w pętli 95k (małe n bazowe – zależne od liczby użytkowników w JSON). Skumulowany efekt: wysokie ciśnienie alokacyjne + CPU mieszany.

## 8. Format wyników i eksport CSV
Każdy test generuje `TestResult`:
```
TestResult(
  testName: String,
  executionTime: Long,   // ms
  details: String,       // meta / parametry
  isSuccessful: Boolean
)
```
CSV per test:
```
iteration,executionTimeMs,details,success
0,512,"threads=8, iterations=1234567",true
...
```
Pliki nazwane: `<test_name_lowercase>_<timestamp>.csv`.

Pliki średnich wartości pojawiają się w sekcji tekstowej UI (append na końcu). Średnia = arytmetyczna z `executionTime`.

## 9. Modele danych
- `User` – używany w RAMTest (serializacja JSON). 
- `Post` – odpowiedź API.
- `TestResult` – kontrakt wymiany między Activity a orkiestratorem.

## 10. Strategia testowania / możliwe rozszerzenia
Aktualne testy: `ExampleUnitTest` + `ExampleInstrumentedTest` (szablony). 

## 11. Dobre praktyki i ograniczenia
- Testy są syntetyczne – nie zastępują realnego profilowania aplikacji produkcyjnej.
- Brak izolacji termicznej: throttling CPU wpłynie na późniejsze wyniki (kolejność testów ma znaczenie).
- Brak warm-up osobno dla JIT/ART (pośrednio wykonywany w pierwszych iteracjach CPU/RAM).
- Dane wyjściowe to tylko czas – brak standard deviation. (Można łatwo dodać.)
- Image/ API test zależą od bieżącej jakości sieci (zalecane Wi-Fi stabilne). 

---
## Szybki start (TL;DR)
1. Uruchom aplikację.
2. Kliknij "Start Suite".
3. Poczekaj ~ (czas sumaryczny ~ CPU 3s + UI 5s + reszta * 30 iteracji ≈ kilka minut w zależności od urządzenia).
4. Po zakończeniu wybierz Export – otrzymasz osobne CSV per test.

## FAQ (skrócone)
- Dlaczego iterations w CPU różnią się między urządzeniami? → Odbiją różnice IPC / taktowania / throttling.
- Czemu RAM test długo trwa? → 95k powtórzeń intensywnej alokacji; to celowe by wywołać presję pamięci/GC.
- Jeśli Location test zwraca błąd? → Sprawdź uprawnienia i czy usługi lokalizacji są aktywne.

---
© 2025 Mobile Benchmark App
