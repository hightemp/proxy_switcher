# Proxy Switcher

A simple and efficient Android application that runs a local HTTP proxy server on your device, allowing you to route your traffic through various upstream proxies (HTTP, HTTPS, SOCKS5).

## 🚀 Features

*   **Local Proxy Server:** Runs a local HTTP proxy on port `8080`.
*   **Upstream Support:** Route traffic through external HTTP, HTTPS, or SOCKS5 proxies.
*   **Authentication:** Supports username/password authentication for upstream proxies.
*   **Proxy Management:** Add, edit, and delete proxies from a local list.
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

## 📱 Usage

1.  **Configure App:**
    *   Open the app and add your upstream proxy (Host, Port, Type, Auth).
    *   Select the proxy from the dropdown on the home screen.
    *   Click **START PROXY**.

2.  **Configure Device:**
    *   Go to your Android **Wi-Fi Settings**.
    *   Modify the current network.
    *   Set **Proxy** to `Manual`.
    *   **Hostname:** `127.0.0.1` (or `localhost`)
    *   **Port:** `8080`
    *   Save settings.

Now all your HTTP/HTTPS traffic will be routed through the selected upstream proxy.