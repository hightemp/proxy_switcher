# Project: Proxy Switcher

## Overview
Android application that acts as a local HTTP/HTTPS/SOCKS5 proxy server, routing traffic through user-configured upstream proxies. Provides a foreground service for persistent proxying, automatic system proxy management via `WRITE_SECURE_SETTINGS`, and a Material 3 Compose UI for proxy CRUD, real-time logs, and system proxy diagnostics.

## Core Features
- Local proxy server on port 8080 (HTTP relay, HTTPS CONNECT tunneling, SOCKS5 upstream)
- Foreground service with wake lock for persistent background operation
- System proxy auto-configuration via `Settings.Global` and per-network WiFi proxy (Android < 10)
- Proxy management: add, edit, delete, enable/disable with Room database persistence
- Real-time log viewer backed by reactive `StateFlow<List<String>>`
- System proxy diagnostic screen (reads `Settings.Global` keys, `dumpsys wifi`)
- Upstream proxy authentication (Basic auth header injection, SOCKS5 USERNAME/PASSWORD)

## Tech Stack
- **Language:** Kotlin 2.0.0 (JVM target 11)
- **Platform:** Android (minSdk 24, targetSdk 35, compileSdk 35)
- **UI:** Jetpack Compose (BOM 2024.04.01) + Material 3
- **Navigation:** Navigation Compose 2.8.0 (string-route NavHost)
- **DI:** Hilt 2.51.1 (KSP 2.0.0-1.0.21)
- **Database:** Room 2.6.1 (single entity, no migrations yet)
- **Build:** Gradle 8.7.2 (AGP), version catalog (`libs.versions.toml`)
- **Preferences:** DataStore 1.1.1 (declared, not yet wired)

## Architecture Notes
- MVVM + Clean Architecture layers: data (Room + Repository) → DI (Hilt modules) → UI (Compose screens + ViewModel)
- ProxyServer runs on `Dispatchers.IO` with `SupervisorJob`; uses `ThreadPoolExecutor` (4–128 threads) for tunnel I/O
- ProxyService is a foreground service managing proxy lifecycle, system proxy state, and wake lock
- Single `@HiltViewModel` (ProxyViewModel) exposes all UI state as `StateFlow`

## Non-Functional Requirements
- Logging: `AppLogger` singleton (StateFlow, capped at 1000 entries) — replaces `android.util.Log`
- Error handling: try/catch at I/O boundaries, `AppLogger.error()` with optional Throwable
- Security: `WRITE_SECURE_SETTINGS` requires ADB grant; upstream auth credentials stored in Room
- Performance: 32 KB tunnel buffers, 256 KB socket buffers, TCP_NODELAY, keepAlive

## Architecture
See .ai-factory/ARCHITECTURE.md for detailed architecture guidelines.
Pattern: MVVM + Clean Architecture
