package com.hightemp.proxy_switcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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
            val current = Settings.Global.getString(contentResolver, Settings.Global.HTTP_PROXY) ?: ""
            getSharedPreferences("proxy_prefs", MODE_PRIVATE)
                .edit()
                .putString("original_proxy", current)
                .apply()
            Settings.Global.putString(contentResolver, Settings.Global.HTTP_PROXY, "127.0.0.1:8080")
            AppLogger.log("ProxyService", "System proxy set to 127.0.0.1:8080 (was: '${current.ifEmpty { "none" }}')") 
        } catch (e: Exception) {
            AppLogger.error("ProxyService", "Failed to set system proxy", e)
        }
    }

    private fun restoreSystemProxy() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val original = getSharedPreferences("proxy_prefs", MODE_PRIVATE)
                .getString("original_proxy", "") ?: ""
            // Pass null to DELETE the key when there was no proxy before;
            // passing "" leaves a broken proxy entry and kills internet on some devices.
            Settings.Global.putString(
                contentResolver,
                Settings.Global.HTTP_PROXY,
                original.ifEmpty { null }
            )
            AppLogger.log("ProxyService", "System proxy restored to: '${original.ifEmpty { "none" }}'")
        } catch (e: Exception) {
            AppLogger.error("ProxyService", "Failed to restore system proxy", e)
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