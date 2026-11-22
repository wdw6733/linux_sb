package sb.linux.client.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sb.linux.client.domain.network.ProxyEnvironment
import sb.linux.client.domain.network.ProxyMode
import sb.linux.client.domain.network.ProxyProfile
import sb.linux.client.domain.network.ProxyScope

/**
 * 多代理路由：按模式 + 环境挑生效 profile。
 * 规则：AUTO 时内网取 enabled 且 scope 为 LAN_ONLY/ALL 的最高优先级（priority 小在前），
 * 外网取 WAN_ONLY/ALL；MANUAL 锁指定 id；OFF 直连。未填好的占位 profile 直接跳过。
 * 环境/生效结果缓存 60s：网络切换时由 [AppNetwork.invalidate] 清缓存，
 * 过期时返回旧值并后台刷新，避免在主线程做 800ms 探测。
 */
internal object ProxyRouter {
    private const val CACHE_TTL_MS = 60_000L
    private const val FAILURE_WINDOW_MS = 5 * 60 * 1000L
    private const val MAX_FAILURES_IN_WINDOW = 3

    private data class CachedResolution(
        val environment: ProxyEnvironment,
        val active: ProxyProfile?,
        val atMs: Long,
    )

    private data class FailureRecord(val count: Int, val lastAtMs: Long)

    @Volatile private var cache: CachedResolution? = null
    private val failures = ConcurrentHashMap<String, FailureRecord>()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var refreshRunning = false

    /** 纯函数：环境→生效 profile 映射（可单元测试）。 */
    fun resolve(
        profiles: List<ProxyProfile>,
        environment: ProxyEnvironment,
        mode: ProxyMode,
        manualId: String?,
    ): ProxyProfile? = candidates(profiles, environment, mode, manualId).firstOrNull()

    /** 纯函数：同环境候选列表（priority 升序），跳过未启用/未填好。 */
    fun candidates(
        profiles: List<ProxyProfile>,
        environment: ProxyEnvironment,
        mode: ProxyMode,
        manualId: String?,
    ): List<ProxyProfile> {
        if (mode == ProxyMode.OFF) return emptyList()
        if (mode == ProxyMode.MANUAL) {
            val locked = profiles.firstOrNull { it.id == manualId }
            return if (locked != null && locked.enabled && locked.isConfigured) listOf(locked) else emptyList()
        }
        val wanted = if (environment.isLan) setOf(ProxyScope.LAN_ONLY, ProxyScope.ALL)
            else setOf(ProxyScope.WAN_ONLY, ProxyScope.ALL)
        return profiles
            .filter { it.enabled && it.isConfigured && it.scope in wanted }
            .sortedWith(compareBy({ it.priority }, { it.name }))
    }

    /** 排除近期连续失败的候选（connectFailed 记）。 */
    fun usableCandidates(
        profiles: List<ProxyProfile>,
        environment: ProxyEnvironment,
        mode: ProxyMode,
        manualId: String?,
    ): List<ProxyProfile> {
        val now = System.currentTimeMillis()
        return candidates(profiles, environment, mode, manualId).filter { profile ->
            val record = failures[profile.id]
            record == null || record.count < MAX_FAILURES_IN_WINDOW ||
                now - record.lastAtMs > FAILURE_WINDOW_MS
        }
    }

    fun recordFailure(profileId: String) {
        val now = System.currentTimeMillis()
        failures.compute(profileId) { _, old ->
            if (old == null || now - old.lastAtMs > FAILURE_WINDOW_MS) FailureRecord(1, now)
            else old.copy(count = old.count + 1, lastAtMs = now)
        }
    }

    fun clearFailures() = failures.clear()

    fun invalidateCache() {
        cache = null
    }

    /** 当前环境（缓存优先；无缓存时同步探测一次）。 */
    fun environment(context: Context): ProxyEnvironment =
        resolveActiveWithCache(context).environment

    /** 当前生效 profile（缓存优先；过期返回旧值并后台刷新）。 */
    fun resolveActive(context: Context): ProxyProfile? =
        resolveActiveWithCache(context).active

    private fun resolveActiveWithCache(context: Context): CachedResolution {
        val appContext = context.applicationContext
        val cached = cache
        val now = System.currentTimeMillis()
        if (cached != null) {
            if (now - cached.atMs < CACHE_TTL_MS) return cached
            refreshAsync(appContext)
            return cached
        }
        return compute(appContext).also { cache = it }
    }

    private fun refreshAsync(appContext: Context) {
        if (refreshRunning) return
        refreshRunning = true
        refreshScope.launch {
            try {
                cache = compute(appContext)
            } finally {
                refreshRunning = false
            }
        }
    }

    private fun compute(appContext: Context): CachedResolution {
        val repo = ProxyProfileRepositoryImpl(appContext)
        repo.ensureSeeded()
        val profiles = repo.profiles()
        val mode = repo.mode()
        // 探测目标取第一条已启用、已填好的内网向 profile；没有就用示例地址。
        val lanTarget = profiles.firstOrNull {
            it.enabled && it.isConfigured && it.scope == ProxyScope.LAN_ONLY
        } ?: profiles.firstOrNull { it.enabled && it.isConfigured && it.scope == ProxyScope.ALL }
        val environment = ProxyEnvironmentDetector.detect(
            appContext,
            lanTarget?.host ?: ProxyProfileRepositoryImpl.LAN_EXAMPLE_HOST,
            lanTarget?.port ?: ProxyProfileRepositoryImpl.LAN_EXAMPLE_PORT,
        )
        val active = resolve(profiles, environment, mode, repo.manualProfileId())
        mirrorLegacy(appContext, active)
        return CachedResolution(environment, active, System.currentTimeMillis())
    }

    /**
     * 旧 proxy_type/host/port 三键只作镜像：解析层/旧备份读它们时不至于看到过期值。
     * 只有值变了才写，避免每次解析都写 SharedPreferences。
     */
    private var lastMirrored: String? = null

    private fun mirrorLegacy(appContext: Context, active: ProxyProfile?) {
        val key = active?.let { "${it.legacyType}|${it.host}|${it.port}" } ?: "0"
        if (key == lastMirrored) return
        lastMirrored = key
        val prefs = appContext.getSharedPreferences("lsb_app_settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (active == null) {
            editor.putInt("proxy_type", 0)
        } else {
            editor.putInt("proxy_type", active.legacyType)
                .putString("proxy_host", active.host)
                .putInt("proxy_port", active.port)
        }
        editor.apply()
    }
}
