package sb.linux.client.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import sb.linux.client.domain.network.ProxyEnvironment

/**
 * 内外网判定。只用 ACCESS_NETWORK_STATE（不要 SSID、不要定位权限）：
 * 三条件全满足才算内网——WiFi 传输 + 本机有 192.168.0.0/16 的 IPv4 +
 * 向内网代理地址 TCP 连通（800ms）且 SOCKS5 无认证握手通过。任一不满足=外网。
 */
internal object ProxyEnvironmentDetector {
    /** 192.168.0.0/16 边界判定（纯函数，可单元测试）。 */
    fun isLanAddress(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val bytes = address.address
        return bytes.size == 4 && bytes[0] == 192.toByte() && bytes[1] == 168.toByte()
    }

    /** 三条件组合（纯函数，可单元测试）。 */
    fun classify(isWifi: Boolean, hasLanAddress: Boolean, lanProbeOk: Boolean): ProxyEnvironment =
        when {
            !isWifi -> ProxyEnvironment(false, "当前不是 WiFi，走外网代理")
            !hasLanAddress -> ProxyEnvironment(false, "本机 IP 不在 192.168.x，走外网代理")
            !lanProbeOk -> ProxyEnvironment(false, "内网代理连不通，走外网代理")
            else -> ProxyEnvironment(true, "WiFi + 192.168.x + 内网代理可连通，走内网代理")
        }

    fun detect(context: Context, lanProbeHost: String, lanProbePort: Int): ProxyEnvironment {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasLanAddress = network
            ?.let { manager.getLinkProperties(it) }
            ?.linkAddresses
            ?.any { isLanAddress(it.address) } == true
        val probeOk = isWifi && hasLanAddress && lanProbeHost.isNotBlank() &&
            lanProbePort in 1..65535 &&
            LocalProxyProbe.socks5(lanProbeHost, lanProbePort, timeoutMs = 800)
        return classify(isWifi, hasLanAddress, probeOk)
    }
}
