package sb.linux.client.data

import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * 代理主机的多地址解析与回退。
 *
 * 背景：域名 lucky.66012943.xyz 同时有 A 记录（36.142.65.200，已死）和 AAAA 记录
 * （2409:8a74:…:baa5，活的）。`InetSocketAddress.createUnresolved(host, port)` 建代理时，
 * java.net 连接只会解析出第一个地址——死的 IPv4，于是报 "Malformed reply from SOCKS server"。
 * OkHttp 的 SOCKS 连接内部用 `new Socket(proxy)`，自定义 SocketFactory 拦截不到代理连接，
 * 所以必须在建 Proxy 之前就把地址解析成可达的那一个。
 *
 * 规则：IP 字面量直接用；域名解析出多个地址时优先 IPv6，逐个做纯 TCP connect 探测
 * （不发 SOCKS 握手），第一个接通的胜出；全部失败则返回排序后的第一个地址，让真实连接
 * 按正常流程报错，走现有的 connectFailed / 候选切换。单地址不探测。
 * 结果按 host:port 缓存 60 秒，select() 每次调用先查缓存，避免每个请求都探测。
 * 注意：这里用系统 DNS 解析代理主机，不动 AppNetwork.dns（它对目标 host 故意排 v4 优先）。
 */
internal object ProxyHostResolver {
    private const val CACHE_TTL_MS = 60_000L
    private const val DEFAULT_PROBE_TIMEOUT_MS = 2500

    private data class CacheEntry(val address: InetSocketAddress, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** IPv6（Inet6Address）在前，同族内保持 DNS 原顺序（sortedBy 是稳定排序）。纯函数，可单测。 */
    fun orderAddresses(addresses: List<InetAddress>): List<InetAddress> =
        addresses.sortedBy { it !is Inet6Address }

    /**
     * IP 字面量直接返回单地址（getAllByName 对字面量不查 DNS）；域名用
     * `InetAddress.getAllByName(host)` 后经 [orderAddresses] 排序；失败返回空 list。
     */
    fun resolveAll(host: String): List<InetAddress> {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching { InetAddress.getAllByName(trimmed).toList() }
            .getOrDefault(emptyList())
            .let(::orderAddresses)
    }

    /**
     * 把代理主机解析成一个已解析的 [InetSocketAddress]，供建 Proxy 用。
     * 多地址时逐个 TCP 探测（单个 [probeTimeoutMs]，默认 2.5 秒），第一个接通的胜出；
     * 全部失败返回排序后的第一个地址；解析失败（空 list）返回未解析地址，让真实连接报错。
     * 结果按 host:port 缓存 60 秒。
     */
    fun resolveProxyAddress(
        host: String,
        port: Int,
        probeTimeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS,
    ): InetSocketAddress {
        val trimmedHost = host.trim()
        val key = "$trimmedHost:$port"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.address }
        val addresses = resolveAll(trimmedHost)
        val resolved: InetSocketAddress = when {
            addresses.isEmpty() -> InetSocketAddress.createUnresolved(trimmedHost, port)
            addresses.size == 1 -> InetSocketAddress(addresses.first(), port)
            else -> probeFirstReachable(addresses, port, probeTimeoutMs.coerceAtLeast(1))
                ?: InetSocketAddress(addresses.first(), port)
        }
        cache[key] = CacheEntry(resolved, now + CACHE_TTL_MS)
        return resolved
    }

    /** 清掉该 host:port 的缓存条目（连接失败后调一次，下次 select() 重新解析探测）。 */
    fun invalidate(host: String, port: Int) {
        cache.remove("${host.trim()}:$port")
    }

    /** 逐个做纯 TCP connect 探测（不发 SOCKS 握手），第一个接通的胜出，全部失败返回 null。 */
    private fun probeFirstReachable(
        addresses: List<InetAddress>,
        port: Int,
        probeTimeoutMs: Int,
    ): InetSocketAddress? {
        for (address in addresses) {
            val ok = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), probeTimeoutMs)
                }
                true
            }.getOrDefault(false)
            if (ok) return InetSocketAddress(address, port)
        }
        return null
    }
}
