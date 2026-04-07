# Proxy Switcher Fix Checklist

## Scope

Fix the review findings from the April 7, 2026 project audit.

## Checklist

- [x] Fix Android lint blockers
  - [x] Guard foreground service start for minSdk 24 in `HomeScreen.kt`
  - [x] Document/suppress intentional `WRITE_SECURE_SETTINGS` manifest usage
- [x] Make proxy service lifecycle accurate
  - [x] Make `ProxyServer.start` report bind success/failure before system proxy is applied
  - [x] Bind local proxy server to loopback only
  - [x] Close active client/upstream sockets during `ProxyServer.stop`
  - [x] Keep UI/service running state aligned with real service broadcasts
- [x] Harden proxy credential and request handling
  - [x] Validate proxy form host/port/auth fields instead of silently defaulting
  - [x] Treat blank passwords safely for HTTP and SOCKS5 auth
  - [x] Inject `Proxy-Authorization` without converting request bodies through `String`
- [x] Reduce credential backup exposure
  - [x] Exclude app database and proxy prefs from backup/data extraction or disable backup
- [x] Clean build and lint hygiene
  - [x] Ignore generated `.kotlin/` diagnostics
  - [x] Move hardcoded Material icons dependency into the version catalog
  - [x] Replace deprecated back arrow icons
  - [x] Address small lint warnings that are cheap and low risk
- [x] Add regression coverage
  - [x] Add focused JVM tests for proxy form validation
  - [x] Add focused JVM tests for HTTP proxy auth header insertion
- [x] Verify
  - [x] Run unit tests
  - [x] Run Android lint

## Device Testing Follow-up

- [x] Fix inverted Home button state found on device
  - [x] Stop using the persisted START_STICKY restart flag as the Home screen's live running status
  - [x] Keep Home button label/color synchronized with the actual foreground service state
  - [x] Verify the button state on a connected device through ADB
- [x] Fix no-internet state after starting proxy on device
  - [x] Bind local proxy server to the same IPv4 loopback address used in system proxy settings
  - [x] Verify `127.0.0.1:8080` accepts connections through ADB
  - [x] Verify HTTPS traffic works through Android system proxy
- [x] Add traffic statistics page
  - [x] Track uploaded and downloaded bytes in the proxy tunnel
  - [x] Track uptime, active tunnels, peak active tunnels, total tunnels, and failures
  - [x] Add a Statistics screen and Home toolbar entry
  - [x] Verify statistics update on device through ADB
- [x] Fix active tunnel growth under load
  - [x] Confirm thread-pool saturation from device logcat
  - [x] Run one pipe direction in the client worker instead of scheduling both directions separately
  - [x] Cap active tunnels before returning `200 Connection Established`
  - [x] Close sockets and record failures when the active tunnel limit is reached
  - [x] Verify short-lived and overloaded CONNECT traffic through ADB
