package com.hightemp.proxy_switcher.proxy

import com.hightemp.proxy_switcher.data.local.ProxyEntity
import com.hightemp.proxy_switcher.data.local.ProxyType
import com.hightemp.proxy_switcher.utils.AppLogger
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory

class ProxyServer {

    companion object {
        // 64 KB tunnel buffer — good balance between RAM and throughput on mobile
        private const val TUNNEL_BUF = 65536
        // 16 KB for the initial HTTP request read — enough for any real-world header set
        private const val REQUEST_BUF = 16384
        // TCP socket buffer hint — increases kernel ring buffer for burst traffic
        private const val SOCK_BUF = 262144 // 256 KB
        // Accept backlog — allow many pending connections before the OS drops them
        private const val BACKLOG = 256
    }

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var upstreamProxy: ProxyEntity? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                        launch { handleClient(clientSocket) }
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

            // Read the initial request line to determine the target
            val buffer = ByteArray(REQUEST_BUF)
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) {
                clientSocket.close()
                return
            }

            val request = String(buffer, 0, bytesRead)
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
                handleHttpsConnect(clientSocket, url, upstreamProxy)
            } else {
                handleHttpRequest(clientSocket, buffer, bytesRead, url, method, upstreamProxy)
            }

        } catch (e: Exception) {
            AppLogger.error("ProxyServer", "Error handling client", e)
            try { clientSocket.close() } catch (ignore: Exception) {}
        }
    }

    private fun handleHttpsConnect(clientSocket: Socket, url: String, proxy: ProxyEntity?) {
        val hostPort = url.split(":")
        val targetHost = hostPort[0]
        val targetPort = if (hostPort.size > 1) hostPort[1].toInt() else 443

        try {
            val upstreamSocket = connectToUpstream(targetHost, targetPort, proxy)

            // Send 200 Connection Established to client
            clientSocket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            clientSocket.getOutputStream().flush()

            tunnel(clientSocket, upstreamSocket)

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

            val upstreamSocket: Socket
            val bufferToWrite: ByteArray
            val bytesToWrite: Int

            if (proxy != null && (proxy.type == ProxyType.HTTP || proxy.type == ProxyType.HTTPS)) {
                // Connect to proxy; use TLS for HTTPS-type proxy
                val tcpSocket = Socket(Proxy.NO_PROXY)
                tcpSocket.connect(InetSocketAddress(proxy.host, proxy.port), 15000)
                applySocketPerf(tcpSocket)
                upstreamSocket = if (proxy.type == ProxyType.HTTPS) {
                    val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(tcpSocket, proxy.host, proxy.port, true) as javax.net.ssl.SSLSocket
                    ssl.startHandshake()
                    ssl
                } else {
                    tcpSocket
                }
                
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
                // Direct or SOCKS: Use connectToUpstream (which handles SOCKS handshake)
                upstreamSocket = connectToUpstream(host, port, proxy)
                bufferToWrite = initialBuffer
                bytesToWrite = initialBytesRead
            }

            upstreamSocket.getOutputStream().write(bufferToWrite, 0, bytesToWrite)

            tunnel(clientSocket, upstreamSocket)

        } catch (e: Exception) {
            AppLogger.error("ProxyServer", "HTTP Request failed: $urlStr", e)
            try { clientSocket.close() } catch (ignore: Exception) {}
        }
    }

    private fun applySocketPerf(socket: Socket) {
        socket.tcpNoDelay = true
        socket.setReceiveBufferSize(SOCK_BUF)
        socket.setSendBufferSize(SOCK_BUF)
        // Hint: favour bandwidth over latency/connection-time (0 = don't care, higher = more important)
        socket.setPerformancePreferences(0, 0, 2)
    }

    private fun connectToUpstream(targetHost: String, targetPort: Int, proxy: ProxyEntity?): Socket {
        if (proxy == null) {
            // IMPORTANT: Use Proxy.NO_PROXY to bypass system proxy settings (avoid recursion)
            val socket = Socket(Proxy.NO_PROXY)
            socket.connect(InetSocketAddress(targetHost, targetPort), 15000)
            applySocketPerf(socket)
            return socket
        }

        // IMPORTANT: Use Proxy.NO_PROXY to connect to the upstream proxy server itself
        val tcpSocket = Socket(Proxy.NO_PROXY)
        tcpSocket.connect(InetSocketAddress(proxy.host, proxy.port), 15000)
        tcpSocket.soTimeout = 15000 // 15s timeout for handshake only
        applySocketPerf(tcpSocket)

        // For HTTPS-type proxy, wrap the TCP socket in TLS before sending any proxy protocol
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
                // For HTTPS target (CONNECT method), we need to tell HTTP proxy to CONNECT to target
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
                
                val inp = socket.getInputStream()
                // Read response (HTTP/1.1 200 ...)
                val responseLine = readLine(inp)
                
                if (!responseLine.contains("200")) {
                    throw IOException("Proxy CONNECT failed. Response: $responseLine")
                }
                // Read until empty line (end of headers)
                var headerLine: String
                while (readLine(inp).also { headerLine = it }.isNotEmpty()) {
                    // AppLogger.log("ProxyServer", "Header: $headerLine")
                }

                // Handshake done — disable read timeout so idle tunnel connections never time out
                socket.soTimeout = 0
                return socket
            }
            ProxyType.SOCKS5 -> {
                // Use buffered streams so SOCKS5 handshake byte reads are not individual syscalls
                val inp = socket.getInputStream().buffered(4096)
                val out = socket.getOutputStream()
                
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
                     out.write(0x01) // Ver 1
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
                // Port (Big Endian)
                out.write((targetPort shr 8) and 0xFF)
                out.write(targetPort and 0xFF)
                out.flush()
                
                // 3. Read response
                val respVer = inp.read()
                val respRep = inp.read()
                if (respRep != 0x00) throw IOException("SOCKS5 Connect failed: $respRep")
                
                // Skip rest of response (RSV, ATYP, BND.ADDR, BND.PORT)
                inp.read() // RSV
                val atyp = inp.read()
                when (atyp) {
                    0x01 -> readNBytes(inp, 4) // IPv4
                    0x03 -> { // Domain
                        val len = inp.read()
                        readNBytes(inp, len)
                    }
                    0x04 -> readNBytes(inp, 16) // IPv6
                }
                readNBytes(inp, 2) // Port

                // Handshake done — disable read timeout so idle tunnel connections never time out
                socket.soTimeout = 0
                return socket
            }
        }
        return socket
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

    /**
     * Bidirectional tunnel between [client] and [server].
     * Each direction runs in its own coroutine on Dispatchers.IO.
     * No flush() in the hot path — SocketOutputStream writes go directly to the kernel TCP buffer.
     */
    private fun tunnel(client: Socket, server: Socket) {
        scope.launch {
            try {
                val src = client.getInputStream()
                val dst = server.getOutputStream()
                val buf = ByteArray(TUNNEL_BUF)
                var n: Int
                while (src.read(buf).also { n = it } != -1) {
                    dst.write(buf, 0, n)
                }
            } catch (_: Exception) {
            } finally {
                try { client.shutdownOutput() } catch (_: Exception) {}
                try { server.close() } catch (_: Exception) {}
            }
        }

        scope.launch {
            try {
                val src = server.getInputStream()
                val dst = client.getOutputStream()
                val buf = ByteArray(TUNNEL_BUF)
                var n: Int
                while (src.read(buf).also { n = it } != -1) {
                    dst.write(buf, 0, n)
                }
            } catch (_: Exception) {
            } finally {
                try { server.shutdownOutput() } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
        }
    }
}