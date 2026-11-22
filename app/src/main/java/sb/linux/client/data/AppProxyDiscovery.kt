package sb.linux.client.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import sb.linux.client.domain.network.ProxyProfile
import sb.linux.client.domain.network.ProxyScope
import sb.linux.client.domain.network.ProxyType

/**
 * 启动和回到前台识别代理；请求等待同一份结果，不阻塞 UI 线程。
 * 角色已收窄：只把系统/本机发现的代理放进多代理候选池（同 host:port 不重复加），
 * 不再直接覆盖唯一配置——旧 proxy_type/host/port 三键不再由这里写。
 */
internal object AppProxyDiscovery {
    @Volatile private var pending: Deferred<Unit>? = null

    @Synchronized fun refresh(context: Context, scope: CoroutineScope) {
        if (pending?.isActive == true) return
        val work = scope.async(start = CoroutineStart.LAZY) {
            val settings = AppSettings(context)
            val before = AppProxyConfig(settings.proxyType, settings.proxyHost, settings.proxyPort)
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val system = runCatching { manager?.defaultProxy }.getOrNull()?.let { proxy ->
                AppProxyConfig(1, proxy.host.orEmpty(), proxy.port).takeIf { it.enabled }
            }
            val found = system ?: AppNetwork.detectLocalProxy(before.port)
            found?.let { config ->
                val repo = ProxyProfileRepositoryImpl(context)
                repo.ensureSeeded()
                val profiles = repo.profiles()
                if (profiles.none { it.host == config.host && it.port == config.port }) {
                    repo.saveAll(
                        profiles + ProxyProfile(
                            name = "自动发现（${config.host}:${config.port}）",
                            type = if (config.type == 2) ProxyType.SOCKS5 else ProxyType.HTTP,
                            host = config.host,
                            port = config.port,
                            scope = ProxyScope.ALL,
                            // 发现的候选排在用户自填的后面，不抢生效位。
                            priority = 10,
                        ),
                    )
                }
            }
        }
        pending = work
        work.start()
    }

    suspend fun awaitReady() { pending?.await() }
}
