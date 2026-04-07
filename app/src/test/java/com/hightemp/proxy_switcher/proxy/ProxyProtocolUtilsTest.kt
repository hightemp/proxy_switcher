package com.hightemp.proxy_switcher.proxy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ProxyProtocolUtilsTest {
    @Test
    fun addProxyAuthorizationHeader_insertsHeaderAfterRequestLineAndPreservesBodyBytes() {
        val body = byteArrayOf(0x00, 0x01, 0x7F, 0x2A)
        val requestHead = "POST http://example.com/upload HTTP/1.1\r\nHost: example.com\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1)
        val request = requestHead + body

        val modified = ProxyProtocolUtils.addProxyAuthorizationHeader(
            initialBuffer = request,
            initialBytesRead = request.size,
            username = "user",
            password = "pass"
        )

        val modifiedHeader = String(
            modified,
            0,
            modified.size - body.size,
            StandardCharsets.ISO_8859_1
        )
        assertTrue(modifiedHeader.startsWith("POST http://example.com/upload HTTP/1.1\r\nProxy-Authorization: Basic dXNlcjpwYXNz\r\n"))
        assertTrue(modifiedHeader.endsWith("Host: example.com\r\n\r\n"))
        assertArrayEquals(body, modified.copyOfRange(modified.size - body.size, modified.size))
    }

    @Test
    fun addProxyAuthorizationHeader_supportsBlankPassword() {
        val request = "GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n"
            .toByteArray(StandardCharsets.ISO_8859_1)

        val modified = ProxyProtocolUtils.addProxyAuthorizationHeader(
            initialBuffer = request,
            initialBytesRead = request.size,
            username = "user",
            password = ""
        )

        val text = String(modified, StandardCharsets.ISO_8859_1)
        assertTrue(text.contains("Proxy-Authorization: Basic dXNlcjo=\r\n"))
    }

    @Test
    fun addProxyAuthorizationHeader_returnsReadBytesWhenHeadersAreIncomplete() {
        val request = "GET http://example.com/ HTTP/1.1\r\nHost: example.com"
            .toByteArray(StandardCharsets.ISO_8859_1)

        val modified = ProxyProtocolUtils.addProxyAuthorizationHeader(
            initialBuffer = request + "extra".toByteArray(StandardCharsets.ISO_8859_1),
            initialBytesRead = request.size,
            username = "user",
            password = "pass"
        )

        assertEquals(request.size, modified.size)
        assertArrayEquals(request, modified)
    }
}
