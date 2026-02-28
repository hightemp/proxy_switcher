package com.hightemp.proxy_switcher.proxy

import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.local.ProxyType
import com.hightemp.proxy_switcher.utils.AppLogger
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

class ProxyServer {

    companion object {
        // 256 KB tunnel buffer — bandwidth-delay product for 300 Mbps / 40 ms RTT = 1.5 MB;
        // larger buf means fewer read/write syscalls per second
        private const val TUNNEL_BUF = 262144
        // 16 KB for the initial HTTP request read — enough for any real-world header set
        private const val REQUEST_BUF = 16384
        // 4 MB socket buffers — required to saturate a 300 Mbps link with 40 ms RTT:
        //   BDP = 37.5 MB/s × 0.04 s = 1.5 MB → 4 MB gives ~3× headroom
        private const val SOCK_BUF = 4 * 1024 * 1024
        // Accept backlog — allow many pending connections before the OS drops them
        private const val BACKLOG = 256
    }

    /**
     * Holds an established upstream connection together with its I/O streams.
     *
     * Critical: for SOCKS5 (and buffered HTTP reads) we wrap [Socket.getInputStream] in a
     * [java.io.BufferedInputStream] during the protocol handshake.  The buffered stream may
     * pre-read application-layer bytes (e.g. the first TLS record from the target server)
     * into its internal buffer.  By carrying that same stream in [input] we guarantee those
     * bytes are forwarded to the client rather than silently discarded when the handshake
     * helper returns.
     */
    private data class ConnectedUpstream(
        val socket: Socket,
        val input: InputStream,
        val output: OutputStream
    )

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var upstreamProxy: ProxyEntity? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ioThreadSeq = AtomicInteger(0)
    private val ioExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "proxy-io-${ioThreadSeq.incrementAndGet()}").apply { isDaemon = true }
    }

    fun start(port: Int, proxy: ProxyEntity?) {
        if (isRunning.get()) return
        upstreamProxy = proxy
        isRunning.set(true)

        scope.launch {
            try {
                serverSocket = ServerSocket(port, BACKLOG)
                AppLogger.log("ProxyServer", "Server started on port $port. Upstream: ${proxy?.host}:${proxy?.port} (${proxy?.type})")

                while (isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        runOnIo("client") { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (isRunning.get()) AppLogger.error("ProxyServer", "Error accepting connection", e)
                    }
                }
            } catch (e: Exception) {
                AppLogger.error("ProxyServer", "Error starting server", e)
                stop()
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            AppLogger.error("ProxyServer", "Error closing server socket", e)
        }
        serverSocket = null
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            // Disable Nagle — prevents 40-200ms delays for interactive/multiplexed traffic
            clientSocket.tcpNoDelay = true
            clientSocket.setReceiveBufferSize(SOCK_BUF)
            clientSocket.setSendBufferSize(SOCK_BUF)

            val input = clientSocket.getInputStream()

            // Read initial proxy request bytes.
            // For CONNECT, some clients may already append TLS bytes after \r\n\r\n in the same packet.
            // Those bytes must be forwarded to upstream or TLS handshakes stall/retry.
            val buffer = ByteArray(REQUEST_BUF)
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) {
                clientSocket.close()
                return
            }

            val headerEnd = findHeaderEnd(buffer, bytesRead)
            val requestLen = if (headerEnd != -1) headerEnd else bytesRead
            val request = String(buffer, 0, requestLen, Charsets.ISO_8859_1)
            val lines = request.split("\r\n")
            val requestLine = lines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                clientSocket.close()
                return
            }

            val method = parts[0]
            val url = parts[1]

            if (method == "CONNECT") {
                val pendingOffset = if (headerEnd != -1) headerEnd else bytesRead
                val pendingLen = (bytesRead - pendingOffset).coerceAtLeast(0)
                handleHttpsConnect(clientSocket, url, upstreamProxy, buffer, pendingOffset, pendingLen)
            } else {
                handleHttpRequest(clientSocket, buffer, bytesRead, url, method, upstreamProxy)
            }

        } catch (e: Exception) {
            AppLogger.error("ProxyServer", "Error handling client", e)
            try { clientSocket.close() } catch (ignore: Exception) {}
        }
    }

    private fun handleHttpsConnect(
        clientSocket: Socket,
        url: String,
        proxy: ProxyEntity?,
        pendingBytes: ByteArray,
        pendingOffset: Int,
        pendingLen: Int
    ) {
        val colon = url.lastIndexOf(':')
        var targetHost = if (colon > 0) url.substring(0, colon) else url
        if (targetHost.startsWith("[") && targetHost.endsWith("]") && targetHost.length > 2) {
            targetHost = targetHost.substring(1, targetHost.length - 1)
        }
        val targetPort = if (colon > 0) url.substring(colon + 1).toIntOrNull() ?: 443 else 443

        try {
            val upstream = connectToUpstream(targetHost, targetPort, proxy)

            // Send 200 Connection Established to client
            clientSocket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientSocket.getOutputStream().flush()

            // Forward any bytes that were already read from client beyond CONNECT headers
            // (typically beginning of TLS ClientHello).
            if (pendingLen > 0) {
                upstream.output.write(pendingBytes, pendingOffset, pendingLen)
            }

            tunnel(clientSocket, upstream)

        } catch (e: Exception) {
            AppLogger.error("ProxyServer", "HTTPS Connect failed: $url. Error: ${e.message}", e)
            try {
                // Send error to client if possible
                val errorMsg = "HTTP/1.1 502 Bad Gateway\r\n\r\n"
                clientSocket.getOutputStream().write(errorMsg.toByteArray())
                clientSocket.close()
            } catch (ignore: Exception) {}
        }
    }

    private fun handleHttpRequest(
        clientSocket: Socket,
        initialBuffer: ByteArray,
        initialBytesRead: Int,
        urlStr: String,
        method: String,
        proxy: ProxyEntity?
    ) {
        try {
            val url = if (urlStr.startsWith("http")) URL(urlStr) else URL("http://$urlStr")
            val host = url.host
            val port = if (url.port != -1) url.port else 80

            var upstream: ConnectedUpstream? = null
            val bufferToWrite: ByteArray
            val bytesToWrite: Int

            if (proxy != null && (proxy.type == ProxyType.HTTP || proxy.type == ProxyType.HTTPS)) {
                // Connect to proxy; use TLS for HTTPS-type proxy.
                // Buffer sizes MUST be set before connect() so TCP window scale is agreed at SYN time.
                val tcpSocket = Socket(Proxy.NO_PROXY)
                tcpSocket.setReceiveBufferSize(SOCK_BUF)
                tcpSocket.setSendBufferSize(SOCK_BUF)
                tcpSocket.connect(InetSocketAddress(proxy.host, proxy.port), 15000)
                applySocketPerf(tcpSocket)
                val upstreamSocket: Socket = if (proxy.type == ProxyType.HTTPS) {
                    val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(tcpSocket, proxy.host, proxy.port, true) as javax.net.ssl.SSLSocket
                    ssl.startHandshake()
                    ssl
                } else {
                    tcpSocket
                }
                upstream = ConnectedUpstream(upstreamSocket, upstreamSocket.getInputStream(), upstreamSocket.getOutputStream())

                if (!proxy.username.isNullOrBlank()) {
                    // Inject header
                    val originalRequest = String(initialBuffer, 0, initialBytesRead)
                    val auth = "${proxy.username}:${proxy.password}"
                    val encoded = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
                    val header = "Proxy-Authorization: Basic $encoded\r\n"

                    // Insert after first line
                    val firstLineEnd = originalRequest.indexOf("\r\n") + 2
                    val modifiedRequest = originalRequest.substring(0, firstLineEnd) + header + originalRequest.substring(firstLineEnd)
                    val modifiedBytes = modifiedRequest.toByteArray()

                    bufferToWrite = modifiedBytes
                    bytesToWrite = modifiedBytes.size
                } else {
                    bufferToWrite = initialBuffer
                    bytesToWrite = initialBytesRead
                }
            } else {
                // Direct or SOCKS: connectToUpstream handles handshake and returns correct streams
                upstream = connectToUpstream(host, port, proxy)
                bufferToWrite = initialBuffer
                bytesToWrite = initialBytesRead
            }

            upstream!!.output.write(bufferToWrite, 0, bytesToWrite)

            tunnel(clientSocket, upstream)

        } catch (e: Exception) {
            AppLogger.error("ProxyServer", "HTTP Request failed: $urlStr", e)
            try { clientSocket.close() } catch (ignore: Exception) {}
        }
    }

    private fun applySocketPerf(socket: Socket) {
        socket.tcpNoDelay = true
        socket.setReceiveBufferSize(SOCK_BUF)
        socket.setSendBufferSize(SOCK_BUF)
        // Keep long-lived Telegram/MTProto tunnels alive through NAT
        socket.keepAlive = true
        // Hint: favour bandwidth over latency/connection-time (0 = don't care, higher = more important)
        socket.setPerformancePreferences(0, 0, 2)
    }

    private fun connectToUpstream(targetHost: String, targetPort: Int, proxy: ProxyEntity?): ConnectedUpstream {
        if (proxy == null) {
            // IMPORTANT: Use Proxy.NO_PROXY to bypass system proxy settings (avoid recursion).
            // Buffer sizes set BEFORE connect() so TCP window scale is negotiated at SYN time.
            val socket = Socket(Proxy.NO_PROXY)
            socket.setReceiveBufferSize(SOCK_BUF)
            socket.setSendBufferSize(SOCK_BUF)
            socket.connect(InetSocketAddress(targetHost, targetPort), 15000)
            applySocketPerf(socket)
            return ConnectedUpstream(socket, socket.getInputStream(), socket.getOutputStream())
        }

        // IMPORTANT: Use Proxy.NO_PROXY to connect to the upstream proxy server itself.
        // Buffer sizes MUST be set before connect() so TCP window scale is negotiated at SYN time.
        val tcpSocket = Socket(Proxy.NO_PROXY)
        tcpSocket.setReceiveBufferSize(SOCK_BUF)
        tcpSocket.setSendBufferSize(SOCK_BUF)
        tcpSocket.connect(InetSocketAddress(proxy.host, proxy.port), 15000)
        tcpSocket.soTimeout = 15000 // 15 s timeout for handshake only; cleared after handshake
        applySocketPerf(tcpSocket)

        // For HTTPS-type proxy, wrap the TCP socket in TLS before sending any proxy protocol.
        val socket: Socket = if (proxy.type == ProxyType.HTTPS) {
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(tcpSocket, proxy.host, proxy.port, true) as javax.net.ssl.SSLSocket
            ssl.startHandshake()
            ssl
        } else {
            tcpSocket
        }

        when (proxy.type) {
            ProxyType.HTTP, ProxyType.HTTPS -> {
                // Tell the HTTP proxy to CONNECT to the target
                val connectReq = StringBuilder()
                connectReq.append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
                connectReq.append("Host: $targetHost:$targetPort\r\n")
                if (!proxy.username.isNullOrBlank()) {
                    val auth = "${proxy.username}:${proxy.password}"
                    val encoded = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
                    connectReq.append("Proxy-Authorization: Basic $encoded\r\n")
                }
                connectReq.append("\r\n")

                val out = socket.getOutputStream()
                out.write(connectReq.toString().toByteArray())
                out.flush()

                // Buffered read for response headers — avoids one syscall per byte.
                // IMPORTANT: we must return this same `inp` from ConnectedUpstream so that
                // any application bytes the buffer may have pre-read are not silently lost.
                val inp = socket.getInputStream().buffered(4096)
                val responseLine = readLine(inp)

                if (!responseLine.contains("200")) {
                    throw IOException("Proxy CONNECT failed. Response: $responseLine")
                }
                // Drain response headers
                var headerLine: String
                while (readLine(inp).also { headerLine = it }.isNotEmpty()) { /* skip */ }

                // Handshake done — disable read timeout so idle tunnel connections never time out
                socket.soTimeout = 0
                return ConnectedUpstream(socket, inp, out)
            }
            ProxyType.SOCKS5 -> {
                val out = socket.getOutputStream()
                // Buffered read for handshake — avoids one syscall per byte AND pre-reads
                // efficiently.  CRITICAL: we must carry `inp` through to ConnectedUpstream;
                // discarding it after the handshake would lose any application-layer bytes
                // that arrived in the same TCP segment as the final SOCKS5 response.
                val inp = socket.getInputStream().buffered(4096)

                // 1. Auth negotiation
                if (!proxy.username.isNullOrBlank()) {
                    out.write(byteArrayOf(0x05, 0x01, 0x02)) // Ver 5, 1 method, User/Pass
                } else {
                    out.write(byteArrayOf(0x05, 0x01, 0x00)) // Ver 5, 1 method, No Auth
                }
                out.flush()

                val ver = inp.read()
                val method = inp.read()

                if (method == 0x02) { // User/Pass auth required
                    out.write(0x01) // Sub-negotiation version
                    val userBytes = proxy.username!!.toByteArray()
                    out.write(userBytes.size)
                    out.write(userBytes)
                    val passBytes = proxy.password!!.toByteArray()
                    out.write(passBytes.size)
                    out.write(passBytes)
                    out.flush()

                    val authVer = inp.read()
                    val authStatus = inp.read()
                    if (authStatus != 0x00) throw IOException("SOCKS5 Auth failed")
                } else if (method != 0x00) {
                    throw IOException("SOCKS5 unsupported method: $method")
                }

                // 2. Connect request
                out.write(0x05) // Ver 5
                out.write(0x01) // Cmd CONNECT
                out.write(0x00) // Rsv
                out.write(0x03) // Atyp Domain
                val domainBytes = targetHost.toByteArray()
                out.write(domainBytes.size)
                out.write(domainBytes)
                out.write((targetPort shr 8) and 0xFF) // Port high byte
                out.write(targetPort and 0xFF)          // Port low byte
                out.flush()

                // 3. Read response
                val respVer = inp.read()
                val respRep = inp.read()
                if (respRep != 0x00) throw IOException("SOCKS5 Connect failed: $respRep")

                // Skip rest of response (RSV, ATYP, BND.ADDR, BND.PORT)
                inp.read() // RSV
                val atyp = inp.read()
                when (atyp) {
                    0x01 -> readNBytes(inp, 4)  // IPv4
                    0x03 -> readNBytes(inp, inp.read()) // Domain (length-prefixed)
                    0x04 -> readNBytes(inp, 16) // IPv6
                }
                readNBytes(inp, 2) // Port

                // Handshake done — disable read timeout so idle tunnel connections never time out.
                // Return `inp` (not socket.getInputStream()) — its internal buffer may already
                // hold the first bytes of application data sent by the remote end.
                socket.soTimeout = 0
                return ConnectedUpstream(socket, inp, out)
            }
        }
        return ConnectedUpstream(socket, socket.getInputStream(), socket.getOutputStream())
    }

    private fun readLine(inp: InputStream): String {
        val sb = StringBuilder()
        var b = inp.read()
        while (b != -1 && b.toChar() != '\n') {
            if (b.toChar() != '\r') sb.append(b.toChar())
            b = inp.read()
        }
        return sb.toString()
    }

    private fun readNBytes(inp: InputStream, n: Int) {
        var remaining = n
        val buf = ByteArray(n)
        while (remaining > 0) {
            val read = inp.read(buf, n - remaining, remaining)
            if (read == -1) break
            remaining -= read
        }
    }

    private fun findHeaderEnd(buffer: ByteArray, len: Int): Int {
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

    /**
     * Bidirectional tunnel between [client] and [server].
     *
     * Each direction runs on its own dedicated daemon thread rather than a coroutine.
     * Reason: Dispatchers.IO has a shared pool capped at 64 threads. In a tight
     * read→write loop that never suspends, coroutines still go through the scheduler
     * on each iteration, adding latency jitter. A daemon thread blocks on read() and
     * writes immediately — zero scheduler overhead, maximising throughput per connection.
     */
    private fun tunnel(client: Socket, upstream: ConnectedUpstream) {
        val closed = AtomicBoolean(false)
        fun closeBoth() {
            if (closed.compareAndSet(false, true)) {
                try { client.close() }          catch (_: Exception) {}
                try { upstream.socket.close() } catch (_: Exception) {}
            }
        }

        daemonThread("c→s") {
            try {
                pipe(client.getInputStream(), upstream.output)
            } finally {
                closeBoth()
            }
        }
        daemonThread("s→c") {
            try {
                // upstream.input may be a BufferedInputStream that already holds pre-read bytes
                // from the SOCKS5/HTTP handshake — using it here ensures those bytes reach the client.
                pipe(upstream.input, client.getOutputStream())
            } finally {
                closeBoth()
            }
        }
    }

    /** Saturates the pipe: reads up to [TUNNEL_BUF] bytes and writes immediately. */
    private fun pipe(src: InputStream, dst: OutputStream) {
        val buf = ByteArray(TUNNEL_BUF)
        try {
            var n: Int
            while (src.read(buf).also { n = it } != -1) {
                dst.write(buf, 0, n)
            }
        } catch (_: SocketException) {
            // Normal during tunnel teardown when peer closes one side first.
        } catch (_: IOException) {
            // Treat transport errors as end-of-stream for this direction.
        }
    }

    private inline fun daemonThread(name: String, crossinline block: () -> Unit) {
        runOnIo(name) {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.error("ProxyServer", "Tunnel thread '$name' failed", e)
            }
        }
    }

    private fun runOnIo(name: String, block: () -> Unit) {
        ioExecutor.execute {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.error("ProxyServer", "IO task '$name' failed", e)
            }
        }
    }
}
