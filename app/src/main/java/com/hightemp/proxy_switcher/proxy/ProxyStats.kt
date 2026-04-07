package com.hightemp.proxy_switcher.proxy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class ProxyStatsSnapshot(
    val isRunning: Boolean = false,
    val startedAtMillis: Long = 0L,
    val stoppedAtMillis: Long = 0L,
    val uploadedBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val activeConnections: Long = 0L,
    val totalConnections: Long = 0L,
    val failedConnections: Long = 0L,
    val peakActiveConnections: Long = 0L
) {
    val totalBytes: Long
        get() = uploadedBytes + downloadedBytes
}

object ProxyStats {
    private const val MIN_EMIT_INTERVAL_MS = 500L

    private val isRunning = AtomicBoolean(false)
    private val startedAtMillis = AtomicLong(0L)
    private val stoppedAtMillis = AtomicLong(0L)
    private val uploadedBytes = AtomicLong(0L)
    private val downloadedBytes = AtomicLong(0L)
    private val activeConnections = AtomicLong(0L)
    private val totalConnections = AtomicLong(0L)
    private val failedConnections = AtomicLong(0L)
    private val peakActiveConnections = AtomicLong(0L)
    private val lastEmitMillis = AtomicLong(0L)

    private val _snapshot = MutableStateFlow(ProxyStatsSnapshot())
    val snapshot: StateFlow<ProxyStatsSnapshot> = _snapshot.asStateFlow()

    fun resetSession(nowMillis: Long = System.currentTimeMillis()) {
        isRunning.set(true)
        startedAtMillis.set(nowMillis)
        stoppedAtMillis.set(0L)
        uploadedBytes.set(0L)
        downloadedBytes.set(0L)
        activeConnections.set(0L)
        totalConnections.set(0L)
        failedConnections.set(0L)
        peakActiveConnections.set(0L)
        emitSnapshot(nowMillis, force = true)
    }

    fun markStopped(nowMillis: Long = System.currentTimeMillis()) {
        isRunning.set(false)
        stoppedAtMillis.set(nowMillis)
        emitSnapshot(nowMillis, force = true)
    }

    fun recordUpload(bytes: Int) {
        if (bytes <= 0) return
        uploadedBytes.addAndGet(bytes.toLong())
        emitSnapshot(System.currentTimeMillis(), force = false)
    }

    fun recordDownload(bytes: Int) {
        if (bytes <= 0) return
        downloadedBytes.addAndGet(bytes.toLong())
        emitSnapshot(System.currentTimeMillis(), force = false)
    }

    fun recordConnectionOpened() {
        val active = activeConnections.incrementAndGet()
        totalConnections.incrementAndGet()
        peakActiveConnections.updateAndGet { current -> maxOf(current, active) }
        emitSnapshot(System.currentTimeMillis(), force = true)
    }

    fun recordConnectionClosed() {
        while (true) {
            val current = activeConnections.get()
            if (current == 0L) break
            if (activeConnections.compareAndSet(current, current - 1L)) break
        }
        emitSnapshot(System.currentTimeMillis(), force = true)
    }

    fun recordFailure() {
        failedConnections.incrementAndGet()
        emitSnapshot(System.currentTimeMillis(), force = true)
    }

    @Synchronized
    private fun emitSnapshot(nowMillis: Long, force: Boolean) {
        val lastEmit = lastEmitMillis.get()
        if (!force && nowMillis - lastEmit < MIN_EMIT_INTERVAL_MS) return

        lastEmitMillis.set(nowMillis)
        _snapshot.value = ProxyStatsSnapshot(
            isRunning = isRunning.get(),
            startedAtMillis = startedAtMillis.get(),
            stoppedAtMillis = stoppedAtMillis.get(),
            uploadedBytes = uploadedBytes.get(),
            downloadedBytes = downloadedBytes.get(),
            activeConnections = activeConnections.get(),
            totalConnections = totalConnections.get(),
            failedConnections = failedConnections.get(),
            peakActiveConnections = peakActiveConnections.get()
        )
    }
}
