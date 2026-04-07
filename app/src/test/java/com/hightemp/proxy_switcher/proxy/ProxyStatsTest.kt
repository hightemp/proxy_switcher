package com.hightemp.proxy_switcher.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyStatsTest {

    @Test
    fun resetSession_clearsCountersAndMarksRunning() {
        ProxyStats.resetSession(nowMillis = 1_000L)
        ProxyStats.recordUpload(512)
        ProxyStats.recordDownload(1024)

        ProxyStats.resetSession(nowMillis = 2_000L)

        val snapshot = ProxyStats.snapshot.value
        assertTrue(snapshot.isRunning)
        assertEquals(2_000L, snapshot.startedAtMillis)
        assertEquals(0L, snapshot.uploadedBytes)
        assertEquals(0L, snapshot.downloadedBytes)
        assertEquals(0L, snapshot.totalConnections)
    }

    @Test
    fun connectionAndByteCounters_updateSnapshot() {
        ProxyStats.resetSession(nowMillis = 1_000L)

        ProxyStats.recordConnectionOpened()
        ProxyStats.recordUpload(2048)
        ProxyStats.recordDownload(4096)
        ProxyStats.recordConnectionClosed()
        ProxyStats.recordFailure()

        val snapshot = ProxyStats.snapshot.value
        assertEquals(2048L, snapshot.uploadedBytes)
        assertEquals(4096L, snapshot.downloadedBytes)
        assertEquals(6144L, snapshot.totalBytes)
        assertEquals(1L, snapshot.totalConnections)
        assertEquals(0L, snapshot.activeConnections)
        assertEquals(1L, snapshot.peakActiveConnections)
        assertEquals(1L, snapshot.failedConnections)
    }

    @Test
    fun markStopped_preservesCountersAndMarksStopped() {
        ProxyStats.resetSession(nowMillis = 1_000L)
        ProxyStats.recordDownload(128)

        ProxyStats.markStopped(nowMillis = 3_000L)

        val snapshot = ProxyStats.snapshot.value
        assertFalse(snapshot.isRunning)
        assertEquals(3_000L, snapshot.stoppedAtMillis)
        assertEquals(128L, snapshot.downloadedBytes)
    }
}
