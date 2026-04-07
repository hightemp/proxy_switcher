# Project Base Rules

> Auto-detected conventions from codebase analysis. Edit as needed.

## Naming Conventions

- Files: PascalCase for Kotlin files (e.g., `ProxyServer.kt`, `HomeScreen.kt`)
- Variables: camelCase (e.g., `proxyList`, `isProxyRunning`, `selectedProxy`)
- Functions: camelCase (e.g., `handleClient`, `connectToUpstream`, `startProxy`)
- Classes: PascalCase (e.g., `ProxyServer`, `ProxyService`, `ProxyViewModel`)
- Constants: UPPER_SNAKE_CASE (e.g., `TUNNEL_BUF`, `REQUEST_BUF`, `MAX_IO_THREADS`)
- Private backing fields: `_camelCase` with public `camelCase` StateFlow exposure
- Database tables: snake_case (e.g., `proxies`)

## Module Structure

- `data/local/` — Room database, DAO, entities (ProxyType enum)
- `data/repository/` — Thin DAO wrappers with `@Inject` constructor
- `di/` — Hilt modules (`@Module @InstallIn(SingletonComponent::class)`)
- `proxy/` — Socket-based proxy server core (HTTP/HTTPS/SOCKS5)
- `service/` — `@AndroidEntryPoint` foreground service
- `ui/screens/` — Stateless Compose screens (receive `NavController` + `ViewModel`)
- `ui/theme/` — Material 3 colour/type tokens
- `ui/viewmodel/` — `@HiltViewModel` classes exposing `StateFlow`
- `utils/` — Singletons (e.g., `AppLogger`)

## Error Handling

- `try/catch` at I/O boundaries (socket operations, database calls)
- Never throw exceptions in UI layer; catch and log
- Use `AppLogger.error(tag, msg, throwable?)` for all error logging
- Service teardown always attempts cleanup even on error
- Daemon thread `UncaughtExceptionHandler` for thread pool tasks

## Logging

- Use `AppLogger.log(tag, msg)` and `AppLogger.error(tag, msg, throwable?)` — NOT `android.util.Log`
- Tags are class names: `"ProxyServer"`, `"ProxyService"`, `"HomeScreen"`
- Entries capped at 1000, format: `[HH:mm:ss] TAG: message`

## Dependency Injection

- `@AndroidEntryPoint` on Activities and Services
- `@HiltViewModel` on ViewModels
- `@Inject constructor` on repositories
- Singleton providers in `di/DatabaseModule.kt` or new `@Module` in `di/`

## State Management

- Expose UI state as `StateFlow` from ViewModels
- Use `stateIn(WhileSubscribed(5000), SharingStarted.Lazily, ...)` for DAO Flow → StateFlow
- Collect in Compose with `collectAsState()`
- Writes via `viewModelScope.launch { repository.xxx() }`

## Compose Patterns

- Stateless composables: receive `NavController` + `ViewModel` via `hiltViewModel()`
- Material 3 components: `Scaffold`, `TopAppBar`, `OutlinedTextField`, `ExposedDropdownMenuBox`
- Navigation: string-route `NavHost`, routes as `const val` strings

## Room / Database

- `exportSchema = false`; bump `version` and add `Migration` for schema changes
- DAO reads return `Flow<>`, writes are `suspend`
- Never call DAO from main thread directly

## Coroutines

- `Dispatchers.IO` + `SupervisorJob` for server/service scopes
- Each client connection is a `launch { }` in ProxyServer
- `viewModelScope` for ViewModel operations
