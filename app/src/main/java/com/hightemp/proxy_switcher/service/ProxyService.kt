package com.hightemp.proxy_switcher.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.hightemp.proxy_switcher.MainActivity
import com.hightemp.proxy_switcher.R
import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.repository.ProxyRepository
import com.hightemp.proxy_switcher.proxy.ProxyServer
import com.hightemp.proxy_switcher.utils.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProxyService : Service() {

    @Inject
    lateinit var repository: ProxyRepository

    private val proxyServer = ProxyServer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "ProxyServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PROXY_ID = "EXTRA_PROXY_ID"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val proxyId = intent.getLongExtra(EXTRA_PROXY_ID, -1L)
                startProxy(proxyId)
            }
            ACTION_STOP -> {
                stopProxy()
            }
        }
        return START_STICKY
    }

    private fun startProxy(proxyId: Long) {
        val notification = createNotification("Starting Proxy...")
        startForeground(1, notification)

        scope.launch {
            val proxy = if (proxyId != -1L) repository.getProxyById(proxyId) else null
            proxyServer.start(8080, proxy)
            applySystemProxy()

            val contentText = if (proxy != null) "Running on :8080 via ${proxy.host}" else "Running on :8080 (Direct)"
            val updatedNotification = createNotification(contentText)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(1, updatedNotification)
        }
    }

    private fun stopProxy() {
        restoreSystemProxy()
        proxyServer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun applySystemProxy() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            AppLogger.log("ProxyService", "WRITE_SECURE_SETTINGS not granted — skipping system proxy setup. Run: adb shell pm grant ${packageName} android.permission.WRITE_SECURE_SETTINGS")
            return
        }
        try {
            // Save all proxy-related keys so we can restore them exactly
            val oldHttpProxy  = Settings.Global.getString(contentResolver, Settings.Global.HTTP_PROXY) ?: ""
            val oldGlobalHost = Settings.Global.getString(contentResolver, "global_http_proxy_host") ?: ""
            val oldGlobalPort = Settings.Global.getString(contentResolver, "global_http_proxy_port") ?: ""
            val oldGlobalExcl = Settings.Global.getString(contentResolver, "global_http_proxy_exclusion_list") ?: ""
            val oldPacUrl     = Settings.Global.getString(contentResolver, "global_proxy_pac_url") ?: ""
            getSharedPreferences("proxy_prefs", MODE_PRIVATE).edit()
                .putString("original_proxy", oldHttpProxy)
                .putString("original_global_host", oldGlobalHost)
                .putString("original_global_port", oldGlobalPort)
                .putString("original_global_excl", oldGlobalExcl)
                .putString("original_pac_url", oldPacUrl)
                .apply()

            // Clear PAC URL first — a non-empty PAC URL overrides all other proxy settings
            contentResolver.delete(Settings.Global.getUriFor("global_proxy_pac_url"), null, null)
            // Set both: http_proxy (legacy) + global_http_proxy_* (used by Chrome and Android system)
            Settings.Global.putString(contentResolver, Settings.Global.HTTP_PROXY, "127.0.0.1:8080")
            Settings.Global.putString(contentResolver, "global_http_proxy_host", "127.0.0.1")
            Settings.Global.putString(contentResolver, "global_http_proxy_port", "8080")
            Settings.Global.putString(contentResolver, "global_http_proxy_exclusion_list", "")
            AppLogger.log("ProxyService", "System proxy set to 127.0.0.1:8080")
            // Also save the current per-network WiFi proxy before changing it
            saveAndSetWifiNetworkProxy("127.0.0.1", 8080)
        } catch (e: Exception) {
            AppLogger.error("ProxyService", "Failed to set system proxy", e)
        }
    }

    private fun restoreSystemProxy() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val prefs = getSharedPreferences("proxy_prefs", MODE_PRIVATE)
            val original     = prefs.getString("original_proxy", "") ?: ""
            val originalHost = prefs.getString("original_global_host", "") ?: ""
            val originalPort = prefs.getString("original_global_port", "") ?: ""
            val originalExcl = prefs.getString("original_global_excl", "") ?: ""
            val originalPac  = prefs.getString("original_pac_url", "") ?: ""

            // Restore or delete http_proxy
            if (original.isEmpty()) {
                contentResolver.delete(Settings.Global.getUriFor(Settings.Global.HTTP_PROXY), null, null)
            } else {
                Settings.Global.putString(contentResolver, Settings.Global.HTTP_PROXY, original)
            }

            // Restore or delete global_http_proxy_* keys
            if (originalHost.isEmpty()) {
                contentResolver.delete(Settings.Global.getUriFor("global_http_proxy_host"), null, null)
                contentResolver.delete(Settings.Global.getUriFor("global_http_proxy_port"), null, null)
                contentResolver.delete(Settings.Global.getUriFor("global_http_proxy_exclusion_list"), null, null)
            } else {
                Settings.Global.putString(contentResolver, "global_http_proxy_host", originalHost)
                Settings.Global.putString(contentResolver, "global_http_proxy_port", originalPort)
                Settings.Global.putString(contentResolver, "global_http_proxy_exclusion_list", originalExcl)
            }

            // Restore or delete PAC URL
            if (originalPac.isEmpty()) {
                contentResolver.delete(Settings.Global.getUriFor("global_proxy_pac_url"), null, null)
            } else {
                Settings.Global.putString(contentResolver, "global_proxy_pac_url", originalPac)
            }

            AppLogger.log("ProxyService", "System proxy restored (http_proxy='${original.ifEmpty { "deleted" }}', global_host='${originalHost.ifEmpty { "deleted" }}')")
            // Also restore the per-network WiFi proxy
            restoreWifiNetworkProxy()
        } catch (e: Exception) {
            AppLogger.error("ProxyService", "Failed to restore system proxy", e)
        }
    }

    /**
     * Saves the current active network's per-network proxy (from LinkProperties), then attempts
     * to set it to the given host:port via WifiManager (best-effort; returns silently on Android 10+
     * for non-system apps, but still logs the active proxy for diagnosis).
     */
    @SuppressLint("MissingPermission")
    private fun saveAndSetWifiNetworkProxy(host: String, port: Int) {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val lp = if (network != null) cm.getLinkProperties(network) else null
            val existingProxy = lp?.httpProxy
            val existingHost = existingProxy?.host ?: ""
            val existingPort = existingProxy?.port?.toString() ?: ""
            getSharedPreferences("proxy_prefs", MODE_PRIVATE).edit()
                .putString("original_wifi_proxy_host", existingHost)
                .putString("original_wifi_proxy_port", existingPort)
                .apply()
            AppLogger.log("ProxyService", "Active network proxy (LinkProperties): '${existingHost}${if (existingPort.isNotEmpty()) ":$existingPort" else ""}'")
            // Attempt to update the WifiConfiguration proxy (works on Android <10, silently fails on 10+)
            attemptSetWifiConfigProxy(host, port)
        } catch (e: Exception) {
            AppLogger.log("ProxyService", "saveAndSetWifiNetworkProxy: ${e.message}")
        }
    }

    /**
     * Restores (or clears) the per-network WiFi proxy to whatever was saved by saveAndSetWifiNetworkProxy.
     */
    @SuppressLint("MissingPermission")
    private fun restoreWifiNetworkProxy() {
        try {
            val prefs = getSharedPreferences("proxy_prefs", MODE_PRIVATE)
            val savedHost = prefs.getString("original_wifi_proxy_host", "") ?: ""
            val savedPort = prefs.getString("original_wifi_proxy_port", "") ?: ""

            if (savedHost.isEmpty()) {
                // Was no proxy before — clear it
                attemptClearWifiConfigProxy()
            } else {
                // Was a different proxy — restore it
                val p = savedPort.toIntOrNull() ?: 0
                attemptSetWifiConfigProxy(savedHost, p)
            }
        } catch (e: Exception) {
            AppLogger.log("ProxyService", "restoreWifiNetworkProxy: ${e.message}")
        }
    }

    /**
     * Attempts to set the active WifiConfiguration's proxy via reflection + WifiManager.updateNetwork().
     * On Android 10+ this returns -1 for non-system apps but does not throw.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun attemptSetWifiConfigProxy(host: String, port: Int) {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val networkId = wifiManager.connectionInfo?.networkId ?: -1
            if (networkId == -1) return

            val config = android.net.wifi.WifiConfiguration()
            config.networkId = networkId
            // Set proxy via reflection on IpConfiguration fields
            val proxyInfoClass = Class.forName("android.net.ProxyInfo")
            val buildMethod = proxyInfoClass.getMethod("buildDirectProxy", String::class.java, Int::class.java)
            val proxyInfoObj = buildMethod.invoke(null, host, port)
            val ipConfigClass = Class.forName("android.net.IpConfiguration")
            val ipConfig = ipConfigClass.newInstance()
            val proxySettingsClass = Class.forName("android.net.IpConfiguration\$ProxySettings")
            val staticValue = proxySettingsClass.getField("STATIC").get(null)
            ipConfigClass.getField("proxySettings").set(ipConfig, staticValue)
            ipConfigClass.getField("httpProxy").set(ipConfig, proxyInfoObj)
            val wifiConfigClass = config.javaClass
            wifiConfigClass.getField("proxySettings").set(config, staticValue)
            wifiConfigClass.getField("httpProxy").set(config, proxyInfoObj)

            val result = wifiManager.updateNetwork(config)
            if (result != -1) {
                wifiManager.saveConfiguration()
                AppLogger.log("ProxyService", "WiFi network proxy set to $host:$port via WifiManager")
            } else {
                AppLogger.log("ProxyService", "WifiManager.updateNetwork returned -1 (restricted on Android 10+) — per-network proxy unchanged")
            }
        } catch (e: Exception) {
            AppLogger.log("ProxyService", "attemptSetWifiConfigProxy: ${e.message}")
        }
    }

    /**
     * Attempts to clear the active WifiConfiguration's proxy (set to ProxySettings.NONE).
     * Silently fails on Android 10+ for non-system apps.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun attemptClearWifiConfigProxy() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val networkId = wifiManager.connectionInfo?.networkId ?: -1
            if (networkId == -1) return

            val config = android.net.wifi.WifiConfiguration()
            config.networkId = networkId
            val ipConfigClass = Class.forName("android.net.IpConfiguration")
            val ipConfig = ipConfigClass.newInstance()
            val proxySettingsClass = Class.forName("android.net.IpConfiguration\$ProxySettings")
            val noneValue = proxySettingsClass.getField("NONE").get(null)
            ipConfigClass.getField("proxySettings").set(ipConfig, noneValue)
            ipConfigClass.getField("httpProxy").set(ipConfig, null)
            val wifiConfigClass = config.javaClass
            wifiConfigClass.getField("proxySettings").set(config, noneValue)
            wifiConfigClass.getField("httpProxy").set(config, null)

            val result = wifiManager.updateNetwork(config)
            if (result != -1) {
                wifiManager.saveConfiguration()
                AppLogger.log("ProxyService", "WiFi network proxy cleared via WifiManager")
            } else {
                AppLogger.log("ProxyService", "WifiManager.updateNetwork returned -1 — manual WiFi proxy clear required: Settings → WiFi → long-press network → Modify → Proxy → None")
            }
        } catch (e: Exception) {
            AppLogger.log("ProxyService", "attemptClearWifiConfigProxy: ${e.message}")
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Proxy Switcher")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure this icon exists or use system icon
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Proxy Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        restoreSystemProxy()
        proxyServer.stop()
    }
}