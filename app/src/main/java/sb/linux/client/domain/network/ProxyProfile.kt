package sb.linux.client.domain.network

import java.util.UUID

/** 代理协议：HTTP/HTTPS 走 CONNECT，SOCKS5 走无认证/用户名密码握手。 */
enum class ProxyType { HTTP, HTTPS, SOCKS5 }

/** 适用环境：仅内网 / 仅外网 / 全部。自动模式按当前环境挑。 */
enum class ProxyScope { LAN_ONLY, WAN_ONLY, ALL }

/** 代理模式：自动按环境切换 / 手动锁定一条 / 直连。 */
enum class ProxyMode { AUTO, MANUAL, OFF }

data class ProxyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val scope: ProxyScope = ProxyScope.ALL,
    val priority: Int = 0,
    val enabled: Boolean = true,
) {
    /**
     * 是否已填好可用：host 为空或端口非法就是占位 profile（等使用者在设置里填），
     * 路由会跳过它，绝不拿空 host 去连。外网“公网域名代理”默认就是这种占位。
     */
    val isConfigured: Boolean get() = host.isNotBlank() && port in 1..65535
    val hasAuth: Boolean get() = username.isNotBlank()

    /** 旧单代理键的兼容类型：0=关闭不存在这里，1=HTTP/HTTPS，2=SOCKS5。 */
    val legacyType: Int get() = if (type == ProxyType.SOCKS5) 2 else 1

    val proxyUrl: String get() {
        val scheme = when (type) {
            ProxyType.SOCKS5 -> "socks"
            ProxyType.HTTPS -> "https"
            ProxyType.HTTP -> "http"
        }
        val formattedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
        return "$scheme://$formattedHost:$port"
    }

    /** 给 OkHttp/WebView 用的显示地址；未填好时返回空串。 */
    val displayAddress: String get() = if (isConfigured) "$host:$port" else "待填写"
}

/** 当前网络环境：内网=WiFi 且本机在 192.168.x 且内网代理可连通，否则外网。 */
data class ProxyEnvironment(
    val isLan: Boolean,
    val reason: String,
)
