# Mobile Benchmark App (React Native / Expo)

Aplikacja React Native (Expo, TypeScript) do syntetycznych testów wydajności: CPU, RAM, UI (animacje Reanimated), ładowanie obrazów z sieci, API request (REST JSON), pozyskanie lokalizacji, + orkiestracja wielu iteracji i eksport wyników. Aktualna implementacja zapisuje wyniki w prostym pliku tekstowym (możliwość rozbudowy do CSV). Można dodać pomiar App Launch Time (obecnie brak jawnego pomiaru – sekcja 12 zawiera sugestię implementacji).

## Spis treści
- [1. Cel](#1-cel)
- [2. Wymagania](#2-wymagania)
- [3. Technologie / Biblioteki (React Native)](#3-technologie--biblioteki-react-native)
- [4. Struktura katalogów / plików](#4-struktura-katalogów--plików)
- [5. Orkiestracja (Benchmark.tsx)](#5-orkiestracja-benchmarktsx)
- [6. Testy](#6-testy)
  - [6.1 CPU Test](#61-cpu-test)
  - [6.2 RAM Test](#62-ram-test)
  - [6.3 UI Test](#63-ui-test)
  - [6.4 Image Loading Test](#64-image-loading-test)
  - [6.5 API Test](#65-api-test)
  - [6.6 Location Test](#66-location-test)
- [7. Złożoność obliczeniowa / pamięciowa](#7-złożoność-obliczeniowa--pamięciowa)
- [8. Format wyniku / model danych](#8-format-wyniku--model-danych)
- [9. Eksport wyników](#9-eksport-wyników)
- [10. Ograniczenia](#10-ograniczenia)
- [11. Szybki start](#11-szybki-start)
- [12. App Launch Time](#12-app-launch-time)
- [13. Licencja / Autor](#13-licencja--autor)

## 1. Cel
Zebranie danych dotyczących wydajności stosu React Native (JS/TS + bridge + nativa). Metryka podstawowa: czas wykonania (ms) poszczególnych scenariuszy.

## 2. Wymagania
- Node.js (zalecane LTS 18+)
- Yarn lub npm (projekt używa Expo – można użyć `npx`)
- Expo CLI (opcjonalnie globalnie) / konto Expo (opcjonalne)
- Android/iOS emulator lub urządzenie fizyczne, dostęp do Internetu
- Uprawnienia lokalizacji (systemowe) dla testu Location

## 3. Technologie / Biblioteki (React Native)
- Expo SDK 53 (`expo`, `expo-router`) – nawigacja i środowisko uruchomieniowe
- React Native 0.79, React 19
- TypeScript (typy modeli wyników)
- `react-native-reanimated` – animacje (UI Test) bez nadmiernego obciążania JS thread
- `expo-image` – ładowanie obrazów (Image Loading Test)
- `expo-location` – lokalizacja / permissions
- `expo-file-system` – zapis wyników do pliku
- `lodash` – potencjalne operacje pomocnicze (obecnie niewielkie użycie)
- Własna prosta implementacja nav-result (promise resolver) do zwrotu wyników testów

## 4. Struktura katalogów / plików
`app/`
- `Benchmark.tsx` – ekran startowy + orkiestracja testów (postęp, statystyki, eksport JSON/CSV)
- `ui-test.tsx` – test animacji (1500 ruchomych kwadratów Reanimated)
- `cpu-test.tsx` – test obciążenia CPU (chunkowane: primes, mnożenie macierzy, operacje FP, sort, log-sum)
- `ram-test.tsx` – test pamięciowy (operacje na listach użytkowników, sortowanie, filtracja, liczniki)
- `image-test.tsx` – sekwencyjne ładowanie 10 obrazów (Picsum) + scroll
- `api-test.tsx` – pobranie JSON (posts) i pomiar latency
- `location-test.tsx` – pobranie pojedynczej lokalizacji (permission flow)
- `services/api.ts` – funkcja `fetchPosts()` (Retrofit analog → fetch / XHR)
- `types.ts` – model `TestResult`, `User`, `Post`
- `utils/navResult.ts` – mechanizm przekazywania wyników przez klucz (promise resolvers)
`assets/users.json` – dane wejściowe dla RAM testu.

(Inne pliki Expo / konfiguracyjne pominięto dla zwięzłości.)

## 5. Orkiestracja (Benchmark.tsx)
- Kolejność testów: UI → CPU → RAM → Image → API → Location.
- Model wykonania: każdy test wykonywany jest 30 razy z rzędu (blokowo), potem przejście do następnego testu.
- Postęp liczony na podstawie zakończonych powtórzeń względem `30 * liczba_testów`.
- Mechanizm przekazywania wyniku: identyczny (klucz w query + `waitForResult/resolveResult`).
- Po zakończeniu całej serii: automatyczny zapis JSON zawierający: surowe wyniki, statystyki per test (avg, median, p95, p99, min, max), agregaty grup, launch time.
- Eksport CSV: dodatkowy przycisk generuje osobne pliki CSV per test z kolumnami: `iteration,executionTimeMs,details`.
- Interfejs ograniczony do dwóch przycisków: Start Tests, Export CSV (JSON eksport wykonywany automatycznie na zakończenie – można powtórzyć ręcznie).

## 6. Testy
### 6.1 CPU Test
Sekwencja chunkowanych zadań (użycie `setTimeout(0)` by oddać JS event loop i uniknąć długiego blokowania UI):
1. Liczenie liczb pierwszych do 600k (w partiach po 5k).
2. Mnożenie dwóch macierzy (120x120) – wiersze w partiach.
3. Ciężkie operacje FP (sqrt, pow) w partiach po 10k iteracji.
4. 8 partii sortowania dużych tablic losowych (150k elementów każda).
5. Sumy logarytmiczno-potęgowe do 1.2M w partiach po 50k.
Wynik: łączny czas; `details` aktualnie puste (można dodać np. checksumy dla weryfikacji przyszłych zmian).

### 6.2 RAM Test
- `RUNS = 95 000` iteracji logicznych (chunk 500); limit czasu `MAX_MS = 10_000` ms (jeśli przekroczony test kończy się wcześniej – zapis w `details`).
- Operacje: klonowanie / shuffle (sort porównujący Math.random), sortowanie alfabetyczne, filtr, uppercase, ograniczanie `bigList` do `MAX_LIST` by kontrolować pamięć, liczniki imion/nazwisk.
- GC nacisk poprzez nadmiarowe alokacje i kopiowanie.

### 6.3 UI Test
- 1500 kwadratów (AnimatedSquare) generowanych jednorazowo (useMemo).
- Każdy animowany parametrem `progress` (Reanimated `withRepeat(withTiming)` → płynne 0↔1).
- Czas trwania testu: ~5s, po czym wynik z czasem animacji.

### 6.4 Image Loading Test
- 10 obrazów (Picsum) ładowanych równolegle komponentem `expo-image` (domyślne cache).
- Na każde `onLoad` przewinięcie scrolla do załadowanego elementu.
- Po zakończeniu wszystkich (lub błędach) agregacja: `Loaded X/10 (failed Y)`.

### 6.5 API Test
- `fetchPosts()` pobiera listę postów (JSONPlaceholder / analogiczny endpoint – implementacja w `services/api.ts`).
- Mierzy czas request→response; wynik zawiera liczbę rekordów.

### 6.6 Location Test
- Sprawdzenie usług lokalizacji + foreground permission.
- Pojedynczy odczyt pozycji z wysoką dokładnością; wynik zawiera współrzędne.

## 7. Złożoność obliczeniowa / pamięciowa
| Test | Czasowa | Pamięć | Uwagi |
|------|---------|--------|-------|
| CPU | O(Z)  | O(n) | Z = suma kosztów primes + matmul + FP + sort + logSum; matmul ~O(n^3) dla n=120 (stała) |
| RAM | O(R * n log n) | O(n + bigList) | R=RUNS (czasowo capowane); n = długość listy users |
| UI | O(k) inicjalizacja | O(k) | k=1500 animowanych elementów |
| Image | O(k) | Bitmapy/cache | k=10 |
| API | O(1) | Niska | pojedynczy request |
| Location | O(1) | Minimalna | pojedynczy odczyt |

## 8. Format wyniku / model danych
`TestResult { testName: string; executionTimeMs: number; details: string; success: boolean; }`

Aktualnie wszystkie iteracje dopisywane są do jednej tablicy w pamięci i serializowane do pliku tekstowego (linia per wynik: `TestName: Xms (details)`). Nie ma jeszcze strukturalnego CSV.

## 9. Eksport wyników
- Auto-zapis JSON po zakończeniu całej serii: `bench_<timestamp>.json` (zawiera statystyki i surowe dane).
- Przycisk Export CSV generuje zestaw plików `bench_<testName>.csv` (1 plik per test) z kolumnami: `iteration,executionTimeMs,details`.
- Surowe wyniki w pamięci można ponownie wyeksportować (JSON/CSV) bez ponownego uruchamiania testów.

## 10. Ograniczenia
- Brak natywnego wątku równoległego dla CPU – użyto chunkowania z `setTimeout` zamiast `JSI/Workers`; wyniki obejmują overhead JS event loop.
- Brak izolacji pomiarów GC / memory footprint (można dodać logi `global.performance.memory` w web lub natywne pluginy – niedostępne standardowo w Expo).
- Brak obecnie pomiaru App Launch Time.
- Wyniki wszystkich testów są łączone w jednym pliku (utrudniona dalsza analiza per test/iteracja).
- Nieweryfikowane checksumy dla CPU – ryzyko niezamierzonej optymalizacji w przyszłości.
- Iterations=3 daje małą próbkę; dla statystyki warto 10+.

## 11. Szybki start
1. Instalacja zależności:
```
npm install
# lub
yarn install
```
2. Uruchom projekt (Android/iOS/web):
```
npx expo start
```
3. Wybierz platformę (a - Android emulator, i - iOS, w - Web) w konsoli Expo.
4. Na ekranie `Benchmark` kliknij `Start Tests`.
5. Poczekaj na zakończenie wszystkich iteracji → komunikat + auto-zapis.
6. (Opcjonalnie) kliknij `Export Results` aby ponowić zapis.

## 12. App Launch Time
Obecnie NIE mierzone. Propozycja implementacji:
- Na samym początku (pierwsza linia pliku wejściowego – np. `index.js` / root layout) zapisz `const launchStart = Date.now()`.
- Po zamontowaniu ekranu `Benchmark` (useEffect) zmierz `Date.now() - launchStart` i wyświetl w nagłówku.
- Zapisz wartość jako pseudo-test (np. `App Launch Time`) i dołącz do eksportu.

## 13. Licencja / Autor
© 2025 Mobile Benchmark App (React Native / Expo variant). Użycie zgodnie z licencjami bibliotek zewnętrznych.
