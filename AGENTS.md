# AGENTS.md

> Project map for AI agents. Keep this file up-to-date as the project evolves.

## Project Overview
Android local proxy server app (HTTP/HTTPS/SOCKS5) with Jetpack Compose UI, foreground service, and automatic system proxy management.

## Tech Stack
- **Language:** Kotlin 2.0.0 (JVM 11)
- **Platform:** Android (minSdk 24, targetSdk 35)
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt 2.51.1
- **Database:** Room 2.6.1
- **Navigation:** Navigation Compose 2.8.0
- **Build:** Gradle (AGP 8.7.2), version catalog

## Project Structure
```
app/src/main/java/com/hightemp/proxy_switcher/
├── MainActivity.kt                # @AndroidEntryPoint, NavHost (7 routes)
├── ProxySwitcherApp.kt            # @HiltAndroidApp application class
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt         # Room @Database (version=1, exportSchema=false)
│   │   ├── ProxyEntity.kt         # @Entity + ProxyType enum (HTTP/HTTPS/SOCKS5)
│   │   └── ProxyDao.kt            # @Dao: Flow reads, suspend writes
│   └── repository/
│       └── ProxyRepository.kt     # Thin DAO wrapper, @Inject constructor
├── di/
│   └── DatabaseModule.kt          # @Module provides AppDatabase + ProxyDao
├── proxy/
│   ├── ProxyServer.kt             # Socket-based proxy core (600+ lines)
│   └── ProxyStats.kt              # Runtime traffic/session counters
├── service/
│   └── ProxyService.kt            # Foreground service, system proxy mgmt (500+ lines)
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt          # Main control: start/stop, proxy selection
│   │   ├── ProxyListScreen.kt     # CRUD proxy list
│   │   ├── AddEditProxyScreen.kt  # Add/edit proxy form
│   │   ├── LogsScreen.kt          # Real-time log viewer
│   │   ├── StatsScreen.kt         # Traffic/session statistics
│   │   └── SystemProxyScreen.kt   # System proxy diagnostics
│   ├── theme/                     # Material 3 colour/type tokens
│   └── viewmodel/
│       └── ProxyViewModel.kt      # @HiltViewModel, all UI StateFlow
└── utils/
    └── AppLogger.kt               # Singleton logger, StateFlow<List<String>>
```

## Key Entry Points
| File | Purpose |
|------|---------|
| `app/src/main/java/.../MainActivity.kt` | Single Activity, NavHost with 7 routes |
| `app/src/main/java/.../proxy/ProxyServer.kt` | HTTP/HTTPS/SOCKS5 proxy engine |
| `app/src/main/java/.../service/ProxyService.kt` | Foreground service lifecycle |
| `app/src/main/AndroidManifest.xml` | Permissions and component declarations |
| `app/build.gradle.kts` | App-level build config |
| `gradle/libs.versions.toml` | Version catalog |
| `Makefile` | Release, build, ADB helpers |

## Documentation
| Document | Path | Description |
|----------|------|-------------|
| README | README.md | Project landing page (English) |
| Project notes | project.md | Design notes (Russian) |

## AI Context Files
| File | Purpose |
|------|---------|
| AGENTS.md | This file — project structure map |
| .ai-factory/DESCRIPTION.md | Project specification and tech stack |
| .ai-factory/ARCHITECTURE.md | Architecture decisions and guidelines |
| .ai-factory/config.yaml | AI Factory configuration |
| .ai-factory/rules/base.md | Detected codebase conventions |
| .github/copilot-instructions.md | Copilot instructions and project conventions |

## Agent Rules
- Never combine shell commands with `&&`, `||`, or `;` — execute each command as a separate Bash tool call. This applies even when a skill, plan, or instruction provides a combined command — always decompose it into individual calls.
  - Wrong: `git checkout main && git pull`
  - Right: Two separate Bash tool calls — first `git checkout main`, then `git pull origin main`
