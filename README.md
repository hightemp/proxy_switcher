# Proxy Switcher

A simple and efficient Android application that runs a local HTTP proxy server on your device, allowing you to route your traffic through various upstream proxies (HTTP, HTTPS, SOCKS5).

## 🚀 Features

*   **Local Proxy Server:** Runs a local HTTP proxy on port `8080`.
*   **Upstream Support:** Route traffic through external HTTP, HTTPS, or SOCKS5 proxies.
*   **Authentication:** Supports username/password authentication for upstream proxies.
*   **Proxy Management:** Add, edit, and delete proxies from a local list.
*   **Auto System Proxy:** Automatically sets the device system proxy to `127.0.0.1:8080` on start and restores the original setting on stop (requires one-time ADB permission grant).
*   **Logs:** Built-in log viewer for debugging connection issues.
*   **Foreground Service:** Keeps the proxy running in the background.

## 🛠 Tech Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material 3)
*   **Architecture:** MVVM + Clean Architecture
*   **Dependency Injection:** Hilt
*   **Database:** Room
*   **Networking:** Custom Socket-based Proxy Core

## 🏃 How to Run

1.  **Clone the repository.**
2.  **Open in Android Studio.**
3.  **Build and Run** on an emulator or physical device.

Alternatively, use Gradle:
```bash
./gradlew installDebug
```

### Grant system proxy permission (one-time)

To allow the app to automatically set and restore the device system proxy, grant the `WRITE_SECURE_SETTINGS` permission via ADB after installation:

```bash
adb shell pm grant com.hightemp.proxy_switcher android.permission.WRITE_SECURE_SETTINGS
```

> This permission is preserved across updates as long as the app is not uninstalled. If you reinstall from scratch, run the command again.

## 📱 Usage

1.  **Configure App:**
    *   Open the app and add your upstream proxy (Host, Port, Type, Auth).
    *   Select the proxy from the dropdown on the home screen.
    *   Click **START PROXY**.

2.  **Configure Device:**

    **Option A — Automatic (recommended):** If you granted `WRITE_SECURE_SETTINGS` via ADB, the app sets `127.0.0.1:8080` as the system proxy automatically when you press **START PROXY** and restores the original setting on **STOP**.

    **Option B — Manual:** Set the proxy in Wi-Fi settings yourself:
    *   Go to **Wi-Fi Settings** → modify the current network.
    *   Set **Proxy** to `Manual`.
    *   **Hostname:** `127.0.0.1`
    *   **Port:** `8080`
    *   Save settings.

Now all your HTTP/HTTPS traffic will be routed through the selected upstream proxy.

## 🔧 ADB Debugging

### Check current system proxy
```bash
adb shell settings get global http_proxy
```
Returns `127.0.0.1:8080` while the proxy is running, or the previous value / `null` after it stops.

### Reset system proxy manually
If the app was killed unexpectedly and the proxy setting was not restored, clear it with:
```bash
adb shell settings delete global http_proxy
```
Or set it back to a specific value:
```bash
adb shell settings put global http_proxy <host>:<port>
```

### View proxy logs in real time
```bash
adb logcat -s ProxyServer:D ProxyService:D
```

## Screenshots

<img src="screenshots/photo_2025-11-22_10-17-00.jpg" width="19%" /> <img src="screenshots/photo_2025-11-22_10-17-08.jpg" width="19%" /> <img src="screenshots/photo_2025-11-22_10-17-13.jpg" width="19%" /> <img src="screenshots/photo_2025-11-22_10-17-18.jpg" width="19%" /> <img src="screenshots/photo_2025-11-22_10-17-22.jpg" width="19%" />

![](https://asdertasd.site/counter/proxy_switcher)