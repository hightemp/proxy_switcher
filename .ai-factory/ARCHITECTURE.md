# Architecture: MVVM + Clean Architecture

## Overview
This project follows MVVM (Model-View-ViewModel) layered on top of Clean Architecture principles, tailored for a single-module Android application. The architecture separates concerns into data, domain (implicit via repository interfaces), presentation (Compose UI + ViewModel), and infrastructure (proxy server, foreground service) layers.

Given the project's scope — a focused proxy management app with a single developer — a full multi-module Clean Architecture would be over-engineering. Instead, we use package-level separation within a single `:app` module, where dependency direction is enforced by convention rather than Gradle module boundaries.

## Decision Rationale
- **Project type:** Android utility app (local proxy server + system proxy management)
- **Tech stack:** Kotlin 2.0.0, Jetpack Compose, Hilt, Room
- **Key factor:** Single-module app with clear layer separation; MVVM is the idiomatic Android pattern with Compose + Hilt + Room

## Folder Structure
```
app/src/main/java/com/hightemp/proxy_switcher/
├── data/                       # DATA LAYER — persistence, external data
│   ├── local/                  # Room database, DAOs, entities
│   │   ├── AppDatabase.kt     # @Database definition
│   │   ├── ProxyDao.kt        # @Dao (Flow reads, suspend writes)
│   │   └── ProxyEntity.kt     # @Entity + ProxyType enum
│   └── repository/             # Repository implementations
│       └── ProxyRepository.kt  # Thin DAO wrapper, @Inject constructor
├── di/                         # DI LAYER — Hilt modules
│   └── DatabaseModule.kt      # @Module: AppDatabase, ProxyDao providers
├── proxy/                      # INFRASTRUCTURE — proxy server engine
│   └── ProxyServer.kt         # Socket-based HTTP/HTTPS/SOCKS5 proxy
├── service/                    # INFRASTRUCTURE — Android service
│   └── ProxyService.kt        # Foreground service, system proxy management
├── ui/                         # PRESENTATION LAYER
│   ├── screens/                # Compose screens (stateless composables)
│   │   ├── HomeScreen.kt
│   │   ├── ProxyListScreen.kt
│   │   ├── AddEditProxyScreen.kt
│   │   ├── LogsScreen.kt
│   │   └── SystemProxyScreen.kt
│   ├── theme/                  # Material 3 tokens
│   └── viewmodel/              # @HiltViewModel classes
│       └── ProxyViewModel.kt
├── utils/                      # CROSS-CUTTING — shared utilities
│   └── AppLogger.kt           # Singleton logger
├── MainActivity.kt             # @AndroidEntryPoint, NavHost
└── ProxySwitcherApp.kt         # @HiltAndroidApp
```

## Dependency Rules

Dependencies flow **inward and downward** — presentation depends on data/repository, never the reverse.

- ✅ `ui/screens/` → `ui/viewmodel/` (screens observe ViewModel state)
- ✅ `ui/viewmodel/` → `data/repository/` (ViewModel calls repository methods)
- ✅ `data/repository/` → `data/local/` (repository delegates to DAO)
- ✅ `service/` → `proxy/` (service manages proxy server lifecycle)
- ✅ `service/` → `ui/viewmodel/` state (service reads proxy config via ViewModel)
- ✅ Any layer → `utils/` (cross-cutting concerns like logging)
- ❌ `data/` → `ui/` (data layer must not know about presentation)
- ❌ `proxy/` → `ui/` (infrastructure must not depend on UI)
- ❌ `data/local/` → `data/repository/` (DAO must not reference repository)
- ❌ Direct DAO usage from screens (always go through ViewModel → Repository)

## Layer Communication

- **Screen → ViewModel:** Compose `collectAsState()` for reads; direct function calls for actions
- **ViewModel → Repository:** `viewModelScope.launch { repository.xxx() }` for writes; `repository.getAllProxies().stateIn(...)` for reactive reads
- **Repository → DAO:** Direct delegation (`suspend` writes, `Flow` reads)
- **Service ↔ Activity:** `Intent` actions (`ACTION_START`, `ACTION_STOP`) via `startForegroundService()`
- **ProxyServer ← ProxyService:** Service creates/destroys server, passes config

## Key Principles

1. **Single ViewModel pattern** — `ProxyViewModel` is the single source of truth for all UI state. All screens share one ViewModel instance via Hilt.
2. **Stateless Composables** — Screens receive `NavController` + `ViewModel` and render state. No business logic in composables.
3. **Reactive data flow** — Room `Flow` → Repository → ViewModel `StateFlow` → Compose `collectAsState()`. Data changes propagate automatically.
4. **Coroutine scope discipline** — `viewModelScope` for UI-driven operations; `CoroutineScope(Dispatchers.IO + SupervisorJob())` for server/service long-running work.
5. **Constructor injection everywhere** — Hilt provides all dependencies. No service locator or manual instantiation.

## Code Examples

### ViewModel exposing reactive state from Room
```kotlin
@HiltViewModel
class ProxyViewModel @Inject constructor(
    private val repository: ProxyRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val proxyList: StateFlow<List<ProxyEntity>> = repository
        .getAllProxies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteProxy(proxy: ProxyEntity) {
        viewModelScope.launch { repository.deleteProxy(proxy) }
    }
}
```

### Stateless Compose screen consuming ViewModel
```kotlin
@Composable
fun ProxyListScreen(
    navController: NavController,
    viewModel: ProxyViewModel = hiltViewModel()
) {
    val proxies by viewModel.proxyList.collectAsState()

    LazyColumn {
        items(proxies) { proxy ->
            ProxyItem(
                proxy = proxy,
                onEdit = { navController.navigate("edit_proxy/${proxy.id}") },
                onDelete = { viewModel.deleteProxy(proxy) }
            )
        }
    }
}
```

### Repository as thin DAO wrapper
```kotlin
class ProxyRepository @Inject constructor(
    private val proxyDao: ProxyDao
) {
    fun getAllProxies(): Flow<List<ProxyEntity>> = proxyDao.getAllProxies()
    suspend fun getProxyById(id: Long): ProxyEntity? = proxyDao.getProxyById(id)
    suspend fun insertProxy(proxy: ProxyEntity) = proxyDao.insertProxy(proxy)
    suspend fun updateProxy(proxy: ProxyEntity) = proxyDao.updateProxy(proxy)
    suspend fun deleteProxy(proxy: ProxyEntity) = proxyDao.deleteProxy(proxy)
}
```

### Hilt module providing database singletons
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "proxy_db").build()

    @Provides
    fun provideProxyDao(database: AppDatabase): ProxyDao = database.proxyDao()
}
```

## Anti-Patterns

- ❌ **Calling DAO directly from a Composable** — always go through ViewModel → Repository
- ❌ **Business logic in Composables** — screens should only render state and forward user actions
- ❌ **Blocking the main thread** — all DAO/network operations must use `suspend` or `Flow` on `Dispatchers.IO`
- ❌ **Creating ViewModel manually** — always use `hiltViewModel()` in Compose or `by viewModels()` in Activity
- ❌ **Exposing MutableStateFlow publicly** — expose `StateFlow` (immutable), keep `MutableStateFlow` private with `_prefix` naming
- ❌ **Using `android.util.Log`** — use `AppLogger.log()` / `AppLogger.error()` so entries appear in LogsScreen
