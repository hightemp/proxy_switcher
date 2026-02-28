---
name: android-proxy-recovery
description: Copilot workflow for Android proxy stickiness, ERR_PROXY_CONNECTION_FAILED after STOP, and Samsung/Android 10+ proxy reset behavior.
---

# Android Proxy Recovery (Copilot)

## Trigger

Use when users report:

- Chrome `ERR_PROXY_CONNECTION_FAILED` after proxy app STOP
- Internet resumes after reboot, then breaks again after START/STOP cycle
- `Settings` look clean but Chrome still behaves as if proxy is active

## Required Checks

```bash
adb shell settings get global http_proxy
adb shell settings get global global_http_proxy_host
adb shell settings get global global_http_proxy_port
adb shell settings get global global_proxy_pac_url
adb shell dumpsys wifi | grep -ni "Proxy settings\|HTTP proxy"
```

## Recovery Rule

Prefer explicit reset:

```bash
adb shell settings put global http_proxy :0
```

Then restart Chrome process and retest network.

## Implementation Guidance

When generating code patches for `ProxyService.kt`:

1. In STOP/restore path, reset `Settings.Global.HTTP_PROXY` to `:0`.
2. Clear `global_http_proxy_*` and PAC with explicit empty/`0` values.
3. Keep per-network Wi-Fi proxy handling disabled on Android 10+ for non-system apps.
4. Keep race protection between `ACTION_START` and `ACTION_STOP` (cancel in-flight start job).

## Done Criteria

- After START/STOP: `http_proxy` is `:0`
- Wi-Fi shows `Proxy settings: NONE`
- Chrome opens `https://example.com` without proxy error
