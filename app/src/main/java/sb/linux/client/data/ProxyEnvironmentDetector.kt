package sb.linux.client.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import java.net.Inet4Address
import java.net.InetAddress
import sb.linux.client.domain.network.ProxyEnvironment

/**
 * 内外网判定。只用 ACCESS_NETWORK_STATE（不要 SSID、不要定位权限）：
 * 本地传输（WiFi 或 VPN）+ 向内网代理地址 TCP 连通（800ms）且 SOCKS5 无认证握手通过
 * 才算内网——握手成功本身就是内网可达的证明（WireGuard 全隧道下传输是 VPN、本机
 * IP 是 10.13.13.x，没有 WiFi 也没有 192.168.x 地址）。任一不满足=外网。
 */
internal object ProxyEnvironmentDetector {
    /** 192.168.0.0/16 边界判定（纯函数，可单元测试；判定本身不再依赖它，保留作诊断）。 */
    fun isLanAddress(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val bytes = address.address
        return bytes.size == 4 && bytes[0] == 192.toByte() && bytes[1] == 168.toByte()
    }

    /** 组合判定（纯函数，可单元测试）：本地传输 + 内网代理握手成功即内网。 */
    fun classify(isLocalTransport: Boolean, lanProbeOk: Boolean): ProxyEnvironment =
        when {
            !isLocalTransport -> ProxyEnvironment(false, "当前不是 WiFi/VPN，走外网代理")
            !lanProbeOk -> ProxyEnvironment(false, "内网代理连不通，走外网代理")
            else -> ProxyEnvironment(true, "本地传输 + 内网代理可连通，走内网代理")
        }

    fun detect(context: Context, lanProbeHost: String, lanProbePort: Int): ProxyEnvironment {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        // WiFi 或 VPN（WireGuard 全隧道）都算本地传输；探测不再被 WiFi/192.168.x
        // 短路——能向内网代理握手成功本身就是内网可达的证明。
        val isLocalTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        // 主线程上不做实时探测：握手含 DNS 解析与建连，主线程会抛
        // NetworkOnMainThreadException（1.2.5 拨代理开关闪退的根因）；只按传输类型
        // 分类，后台刷新（ProxyRouter.refreshAsync）会再探测纠正。
        val probeOk = if (Looper.myLooper() == Looper.getMainLooper()) false
        else lanProbeHost.isNotBlank() &&
            lanProbePort in 1..65535 &&
            LocalProxyProbe.socks5(lanProbeHost, lanProbePort, timeoutMs = 800)
        return classify(isLocalTransport, probeOk)
    }
}
