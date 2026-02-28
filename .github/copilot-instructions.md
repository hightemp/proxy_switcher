# Proxy Switcher – Copilot Instructions

## Skills

- Proxy recovery workflow for sticky Android proxy state:
  `.github/skills/android-proxy-recovery/SKILL.md`

## Architecture

MVVM + Clean Architecture on Android (Kotlin, Jetpack Compose, Hilt, Room).

```
app/src/main/java/com/hightemp/proxy_switcher/
├── data/local/            # Room DB: AppDatabase, ProxyDao, ProxyEntity (ProxyType enum)
├── data/repository/       # ProxyRepository – thin DAO wrapper, @Inject constructor
├── di/                    # DatabaseModule – SingletonComponent, provides AppDatabase + ProxyDao
├── proxy/                 # ProxyServer.kt – socket-based HTTP/HTTPS/SOCKS5 proxy core
├── service/               # ProxyService.kt – @AndroidEntryPoint foreground service, port 8080
├── ui/
│   ├── screens/           # HomeScreen, ProxyListScreen, AddEditProxyScreen, LogsScreen
│   ├── theme/             # Material 3 colour/type tokens
│   └── viewmodel/         # ProxyViewModel (@HiltViewModel)
└── utils/                 # AppLogger – singleton StateFlow<List<String>>, capped at 1000
```

## Code Style

- Kotlin only; JVM target 11, compileSdk/targetSdk 35, minSdk 24.
- Follow existing Compose patterns: stateless composables receive `NavController` + `ViewModel` via `hiltViewModel()`.
- DAO functions use `suspend` for writes and `Flow` for reads; never call them from the main thread directly.
- `ProxyServer` runs entirely on `Dispatchers.IO` using `CoroutineScope + SupervisorJob`; each client is a `launch { }`.

## Build & Test

```bash
# Install debug APK to connected device
./gradlew installDebug

# Build debug APK only (output: app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

Gradle wrapper is committed; do not override the Gradle or AGP versions without updating `gradle/libs.versions.toml`.

## Key Library Versions (libs.versions.toml)

| Library          | Version       |
|------------------|---------------|
| AGP              | 8.7.2         |
| Kotlin           | 2.0.0         |
| KSP              | 2.0.0-1.0.21  |
| Hilt             | 2.51.1        |
| Room             | 2.6.1         |
| Navigation Compose | 2.8.0       |
| Compose BOM      | 2024.04.01    |

## Project Conventions

- **DI** – Always annotate new Activities/Services with `@AndroidEntryPoint`; new ViewModels with `@HiltViewModel`; new singleton providers go in `di/DatabaseModule.kt` or a new `@Module` in `di/`.
- **Navigation** – String-route `NavHost` in `MainActivity`. Add new routes as `const val` strings. Pass arguments via `NavType` (see `edit_proxy/{proxyId}` example).
- **Logging** – Use `AppLogger.log(tag, msg)` / `AppLogger.error(tag, msg)` rather than `android.util.Log` so entries appear in `LogsScreen`.
- **State** – Expose UI state as `StateFlow` from ViewModels using `stateIn(WhileSubscribed(5000), SharingStarted…)`. Collect in Compose with `collectAsState()`.
- **ProxyServer** – Supports `CONNECT` (HTTPS tunneling), plain HTTP relay, and SOCKS5 upstream. Upstream auth is injected as `Proxy-Authorization: Basic` header.
- **Room schema** – `exportSchema = false`; bump `version` and add a `Migration` in `AppDatabase.kt` for any schema change.
- **DataStore** – declared in `libs.versions.toml` (v1.1.1) but not yet wired up; prefer it over SharedPreferences for any new settings.
