package sb.linux.client.data

import java.net.InetSocketAddress
import java.net.Socket

/** Probes the two local proxy protocols supported by automatic proxy discovery. */
internal object LocalProxyProbe {
    fun socks5(port: Int): Boolean = socks5("127.0.0.1", port)

    /**
     * 任意 host 的 SOCKS5 无认证握手探测（内网 192.168.124.250:57080 也走这里）。
     * 域名解析出多个地址时逐个试握手，任一成功即 true——只试第一个地址会被死的 v4 卡住。
     * 解析失败时退回旧行为（按原始 host 直连一次）。
     */
    fun socks5(host: String, port: Int, timeoutMs: Int = 300): Boolean {
        val addresses = ProxyHostResolver.resolveAll(host)
        if (addresses.isEmpty()) return socks5Handshake(InetSocketAddress(host, port), timeoutMs)
        return addresses.any { socks5Handshake(InetSocketAddress(it, port), timeoutMs) }
    }

    private fun socks5Handshake(address: InetSocketAddress, timeoutMs: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(address, timeoutMs)
            socket.soTimeout = 400
            socket.getOutputStream().apply { write(byteArrayOf(0x05, 0x01, 0x00)); flush() }
            val input = socket.getInputStream()
            input.read() == 0x05 && input.read() == 0x00
        }
    }.getOrDefault(false)

    fun http(port: Int): Boolean = http("127.0.0.1", port)

    /** 与 [socks5] 同理：多地址逐个试 CONNECT，任一成功即 true；解析失败退回旧行为。 */
    fun http(host: String, port: Int, timeoutMs: Int = 300): Boolean {
        val addresses = ProxyHostResolver.resolveAll(host)
        if (addresses.isEmpty()) return httpProbe(InetSocketAddress(host, port), timeoutMs)
        return addresses.any { httpProbe(InetSocketAddress(it, port), timeoutMs) }
    }

    private fun httpProbe(address: InetSocketAddress, timeoutMs: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(address, timeoutMs)
            socket.soTimeout = 500
            socket.getOutputStream().apply {
                write("CONNECT 127.0.0.1:1 HTTP/1.1\r\nHost: 127.0.0.1:1\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
            val line = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine().orEmpty()
            val status = line.split(' ').getOrNull(1)?.toIntOrNull()
            line.startsWith("HTTP/") && status in setOf(200, 400, 403, 407, 500, 502, 503)
        }
    }.getOrDefault(false)
}
