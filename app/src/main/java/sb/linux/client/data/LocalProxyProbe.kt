package sb.linux.client.data

import android.os.Looper
import java.net.InetSocketAddress
import java.net.Socket

/** Probes the two local proxy protocols supported by automatic proxy discovery. */
internal object LocalProxyProbe {
    /**
     * 主线程上绝不做任何网络操作（DNS 解析、建连都会抛 NetworkOnMainThreadException）：
     * 探测只在后台线程有意义，主线程调用直接返回 false，调用方按“探测失败”分类，
     * 后台刷新会再探测纠正。1.2.5 拨代理开关闪退就是主线程上走了这里的 DNS。
     */
    private fun onMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    fun socks5(port: Int): Boolean = socks5("127.0.0.1", port)

    /**
     * 任意 host 的 SOCKS5 无认证握手探测（内网 192.168.124.250:57080 也走这里）。
     * 域名解析出多个地址时逐个试握手，任一成功即 true——只试第一个地址会被死的 v4 卡住。
     * 解析不到不做任何回退（1.2.5 的“按原始 host 直连一次”会在
     * `InetSocketAddress(host, port)` 构造里做 DNS 查询，主线程上直接闪退，见 1.2.6）。
     */
    fun socks5(host: String, port: Int, timeoutMs: Int = 300): Boolean {
        if (onMainThread()) return false
        val addresses = ProxyHostResolver.resolveAll(host)
        if (addresses.isEmpty()) return false
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

    /** 与 [socks5] 同理：多地址逐个试 CONNECT，任一成功即 true；解析不到直接返回 false。 */
    fun http(host: String, port: Int, timeoutMs: Int = 300): Boolean {
        if (onMainThread()) return false
        val addresses = ProxyHostResolver.resolveAll(host)
        if (addresses.isEmpty()) return false
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
