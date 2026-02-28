---
name: android-proxy-recovery
description: Diagnose and fix Android proxy stickiness when Chrome shows ERR_PROXY_CONNECTION_FAILED after stopping a local proxy app. Use when proxy settings appear cleared but traffic is still forced through stale proxy state.
---

# Android Proxy Recovery

## When To Use

Use this skill when Android/Chrome keeps failing with `ERR_PROXY_CONNECTION_FAILED` after proxy stop, especially on Samsung or Android 10+.

## Core Facts

- `Settings.Global.HTTP_PROXY` can be sticky on some stacks.
- Clearing via `delete` may be insufficient; forcing `:0` is often effective.
- Per-network Wi-Fi proxy is separate from `Settings.Global` and is restricted for normal apps on Android 10+.

## Quick Diagnosis (ADB)

Run these checks:

```bash
adb shell settings get global http_proxy
adb shell settings get global global_http_proxy_host
adb shell settings get global global_http_proxy_port
adb shell settings get global global_proxy_pac_url
adb shell dumpsys wifi | grep -ni "Proxy settings\|HTTP proxy"
adb shell dumpsys connectivity | grep -ni "httpProxy\|mGlobalProxy\|mDefaultProxy\|ProxyInfo"
```

Interpretation:

- If `http_proxy` is `127.0.0.1:8080` after STOP, clear is broken.
- If Wi-Fi shows `Proxy settings: STATIC` or `HTTP proxy: ...`, this is per-network proxy.

## Immediate Recovery

```bash
adb shell settings put global http_proxy :0
adb shell am force-stop com.android.chrome
adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main -a android.intent.action.VIEW -d https://example.com
```

## Code Fix Pattern (Kotlin)

In `ProxyService.restoreSystemProxy()`:

1. Force-clear sticky HTTP proxy:

```kotlin
Settings.Global.putString(contentResolver, Settings.Global.HTTP_PROXY, ":0")
```

2. Clear related keys with explicit values (not delete-only paths):

```kotlin
Settings.Global.putString(contentResolver, "global_http_proxy_host", "")
Settings.Global.putString(contentResolver, "global_http_proxy_port", "0")
Settings.Global.putString(contentResolver, "global_http_proxy_exclusion_list", "")
Settings.Global.putString(contentResolver, "global_proxy_pac_url", "")
```

3. On Android 10+, do not attempt per-network Wi-Fi proxy writes for non-system apps.

## Validation Workflow

1. Start app proxy.
2. Stop app proxy.
3. Verify:

```bash
adb shell settings get global http_proxy   # expected :0
adb shell settings get global global_http_proxy_host # expected empty
adb shell dumpsys wifi | grep -ni "Proxy settings" # expected NONE
```

4. Open Chrome URL and confirm no `ERR_PROXY_CONNECTION_FAILED`.
