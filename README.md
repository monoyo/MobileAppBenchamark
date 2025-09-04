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
11. Dobre praktyki i ograniczenia
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
- Gradle Wrapper (Version Catalog `libs.*`)
- minSdk = 24, targetSdk = 35, compileSdk = 35
- Dostęp do Internetu (test API + ładowanie obrazów – Coil/Ktor)
- Uprawnienia lokalizacji (Location Test)

## 3. Konfiguracja / Budowanie
1. Sklonuj repo.
2. Otwórz w Android Studio.
3. Gradle sync (automatycznie zaciągnie Coil + Ktor).
4. Build: `assembleDebug`.
5. Uruchom na urządzeniu fizycznym (zalecane).

## 4. Struktura pakietów i modułów
`com.jossy.android.mobilebenchmarkappkotlin`
- `activity/` – ekrany testów.
- `data/` – modele danych + wynik testu.
- `service/` – klient API (Ktor: `ApiService`).
- `CPUTest.kt`, `RAMTest.kt` – logika testów syntetycznych.

Orkiestracja: `BenchmarkSuiteActivity`.

## 5. Użyte biblioteki (po migracji)
| Biblioteka | Rola |
|-----------|------|
| AndroidX Core/AppCompat/ConstraintLayout/RecyclerView | Podstawy UI |
| Material Components | Elementy Material |
| Kotlin Coroutines (Android, Play Services) | Współbieżność testów, asynchroniczność |
| Kotlinx Serialization JSON | Serializacja (RAMTest, API) |
| Coil | Ładowanie i dekodowanie obrazów (Image Loading Test) |
| Ktor Client (core, android, logging, content-negotiation, serialization) | HTTP (API Test) |
| Play Services Location | Lokalizacja |
| Lifecycle Runtime KTX | `lifecycleScope` w aktywnościach (API test) |

Usunięte: Glide, Retrofit, OkHttp (zastąpione przez Coil + Ktor). 

## 6. Opis testów i przepływ działania (flow)
### Suite
- 6 testów * 30 iteracji (konfigurowalne w kodzie) sekwencyjnie.
- Zbiór wyników → podsumowanie + eksport CSV.

### CPU Test
- Równoległe korutyny (Default dispatcher) = liczba rdzeni.
- Ciasna pętla funkcji `sin/cos/sqrt` do upłynięcia limitu czasu.

### RAM Test
- `RUNS = 95_000` powtórzeń transformacji kolekcji + JSON (wysoka presja alokacyjna).

### Image Loading Test
- 10 URL (Picsum).
- Coil ładuje obrazy w `RecyclerView`; mierzy czas do przetworzenia (sukces/porażka) wszystkich żądań.

### API Test
- Ktor GET `/posts` (JSONPlaceholder).
- Mierzy latency request→response; dane dekodowane przez kotlinx.serialization.

### UI Test
- Generowanie 1500 widoków + animacje property.

### Location Test
- Pomiar czasu pobrania aktualnej lokalizacji (Fused Provider).

### Application Init
- StrictMode w debug + pre-warm `Coil.imageLoader` w wątku tła.

## 7. Analiza złożoności
(bez zmian vs wersja sprzed migracji – charakterystyka obciążeń identyczna, zmienił się tylko stos HTTP/obrazów)

| Test | Złożoność dominująca | Uwagi |
|------|----------------------|-------|
| CPU | O(T * I) | Operacje FPU trygonometryczne |
| RAM | O(R * n log n) | sort/shuffle + JSON |
| Image | O(k) | k = liczba obrazów (10), zależne od sieci/cache |
| API | O(1) | Pojedynczy request |
| UI | O(n) | n = liczba wygenerowanych View |
| Location | O(1) | Zależne od providerów |

## 8. Format wyników i eksport CSV
Struktura `TestResult` niezmieniona.

## 9. Modele danych
`Post` oznaczony `@Serializable` (dla Ktor + kotlinx.serialization).

## 10. Strategia testowania
Propozycje: test mock API z Ktor `MockEngine`, test skrócony RAM (RUNS małe), walidacja `iterations > 0` w CPU przy małym `durationMs`.

## 11. Dobre praktyki i ograniczenia
Sieć (API/Image) podatna na jitter; dla stabilności można dodać retry lub lokalny serwer mock.

## 12. Dalszy rozwój
- Percentyle (P95) czasów.
- Rejestr użycia pamięci (PSS) w trakcie.
- Macrobenchmark / Baseline Profiles dla start-up.

---
Szybki start: Uruchom → Start Suite → Export.

Migracja: Glide→Coil, Retrofit/OkHttp→Ktor wykonana w kodzie (`ImageLoadingActivity`, `ApiTestActivity`, `ApiService`, `BenchmarkApplication`).
