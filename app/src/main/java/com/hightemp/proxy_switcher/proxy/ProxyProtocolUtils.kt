package com.hightemp.proxy_switcher.proxy

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object ProxyProtocolUtils {
    fun addProxyAuthorizationHeader(
        initialBuffer: ByteArray,
        initialBytesRead: Int,
        username: String,
        password: String?
    ): ByteArray {
        val headerEnd = findHeaderEnd(initialBuffer, initialBytesRead)
        if (headerEnd == -1) {
            return initialBuffer.copyOf(initialBytesRead)
        }

        val firstLineEnd = findLineEnd(initialBuffer, headerEnd)
        if (firstLineEnd == -1) {
            return initialBuffer.copyOf(initialBytesRead)
        }

        val header = proxyAuthorizationHeader(username, password)
            .toByteArray(StandardCharsets.ISO_8859_1)

        return ByteArrayOutputStream(initialBytesRead + header.size).apply {
            write(initialBuffer, 0, firstLineEnd)
            write(header)
            write(initialBuffer, firstLineEnd, initialBytesRead - firstLineEnd)
        }.toByteArray()
    }

    fun findHeaderEnd(buffer: ByteArray, len: Int): Int {
        if (len < 4) return -1
        var i = 0
        while (i <= len - 4) {
            if (buffer[i] == '\r'.code.toByte() &&
                buffer[i + 1] == '\n'.code.toByte() &&
                buffer[i + 2] == '\r'.code.toByte() &&
                buffer[i + 3] == '\n'.code.toByte()
            ) {
                return i + 4
            }
            i++
        }
        return -1
    }

    fun proxyAuthorizationHeader(username: String, password: String?): String {
        val auth = "$username:${password.orEmpty()}"
        val encoded = encodeBase64(auth.toByteArray(StandardCharsets.UTF_8))
        return "Proxy-Authorization: Basic $encoded\r\n"
    }

    private fun findLineEnd(buffer: ByteArray, len: Int): Int {
        var i = 0
        while (i <= len - 2) {
            if (buffer[i] == '\r'.code.toByte() && buffer[i + 1] == '\n'.code.toByte()) {
                return i + 2
            }
            i++
        }
        return -1
    }

    private fun encodeBase64(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0

            output.append(alphabet[b0 shr 2])
            output.append(alphabet[((b0 and 0x03) shl 4) or (b1 shr 4)])
            output.append(if (i + 1 < bytes.size) alphabet[((b1 and 0x0F) shl 2) or (b2 shr 6)] else '=')
            output.append(if (i + 2 < bytes.size) alphabet[b2 and 0x3F] else '=')
            i += 3
        }
        return output.toString()
    }
}
