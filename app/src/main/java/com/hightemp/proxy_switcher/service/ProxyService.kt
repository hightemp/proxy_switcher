package com.hightemp.proxy_switcher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hightemp.proxy_switcher.MainActivity
import com.hightemp.proxy_switcher.R
import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.repository.ProxyRepository
import com.hightemp.proxy_switcher.proxy.ProxyServer
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
            
            val contentText = if (proxy != null) "Running on :8080 via ${proxy.host}" else "Running on :8080 (Direct)"
            val updatedNotification = createNotification(contentText)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(1, updatedNotification)
        }
    }

    private fun stopProxy() {
        proxyServer.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        proxyServer.stop()
    }
}