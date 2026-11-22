package sb.linux.client.domain.network

import java.io.Closeable

data class WebViewDohPolicy(
    val proxyUrl: String?,
    val dohActive: Boolean,
    val dohUrl: String,
    val forWebsites: Boolean = true,
    /** 当前生效的代理 profile（WebView 认证、状态显示用；直连时为 null）。 */
    val proxyProfile: ProxyProfile? = null,
)

interface WebViewDohTunnel : Closeable {
    val port: Int
}

interface WebViewDohNetworkRepository {
    suspend fun awaitReady()
    fun currentPolicy(): WebViewDohPolicy
    fun openTunnel(): WebViewDohTunnel
}
