package sb.linux.client.ui.feature.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import sb.linux.client.data.LocalProxyProbe
import sb.linux.client.data.ProxyRouter
import sb.linux.client.domain.network.ProxyMode
import sb.linux.client.domain.network.ProxyProfile
import sb.linux.client.domain.network.ProxyProfileRepository
import sb.linux.client.domain.network.ProxyScope
import sb.linux.client.domain.network.ProxyType

/** 编辑中的一行 profile（port/priority 用字符串，方便空着当占位）。 */
data class ProxyProfileEditState(
    val id: String,
    val name: String,
    val type: ProxyType,
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val scope: ProxyScope,
    val priority: String,
    val enabled: Boolean,
)

data class ProxyProfilesUiState(
    val profiles: List<ProxyProfile>,
    val mode: ProxyMode,
    val manualId: String?,
    val failoverDirect: Boolean,
    val editing: ProxyProfileEditState? = null,
    val editError: String? = null,
    val testingId: String? = null,
    val testResults: Map<String, String> = emptyMap(),
    val feedback: String? = null,
)

class ProxyProfilesViewModel(
    private val repository: ProxyProfileRepository,
) : ViewModel() {
    var state by mutableStateOf(loadState())
        private set

    private fun loadState() = ProxyProfilesUiState(
        profiles = repository.profiles(),
        mode = repository.mode(),
        manualId = repository.manualProfileId(),
        failoverDirect = repository.failoverDirect(),
    )

    fun refresh() {
        val editing = state.editing
        val testResults = state.testResults
        state = loadState().copy(editing = editing, testResults = testResults)
    }

    fun consumeFeedback() = update { copy(feedback = null) }

    fun startAdd() {
        val nextPriority = (state.profiles.maxOfOrNull { it.priority } ?: -1) + 1
        update {
            copy(
                editing = ProxyProfileEditState(
                    id = UUID.randomUUID().toString(),
                    name = "",
                    type = ProxyType.HTTP,
                    host = "",
                    port = "",
                    username = "",
                    password = "",
                    scope = ProxyScope.ALL,
                    priority = nextPriority.toString(),
                    enabled = true,
                ),
                editError = null,
            )
        }
    }

    fun startEdit(profile: ProxyProfile) {
        update {
            copy(
                editing = ProxyProfileEditState(
                    id = profile.id,
                    name = profile.name,
                    type = profile.type,
                    host = profile.host,
                    port = if (profile.port in 1..65535) profile.port.toString() else "",
                    username = profile.username,
                    password = profile.password,
                    scope = profile.scope,
                    priority = profile.priority.toString(),
                    enabled = profile.enabled,
                ),
                editError = null,
            )
        }
    }

    fun updateEdit(change: ProxyProfileEditState.() -> ProxyProfileEditState) {
        val editing = state.editing ?: return
        update { copy(editing = editing.change(), editError = null) }
    }

    fun cancelEdit() = update { copy(editing = null, editError = null) }

    /** 保存编辑。host 留空=占位（等以后填），允许保存；host 填了则端口必须有效。 */
    fun saveEdit() {
        val editing = state.editing ?: return
        val name = editing.name.trim().ifBlank { "代理" }
        val host = editing.host.trim()
        val port = editing.port.toIntOrNull() ?: 0
        if (host.isNotBlank() && (port !in 1..65535 || host.contains("://") || host.contains('/') || host.any(Char::isWhitespace))) {
            update { copy(editError = "请输入有效的代理主机和 1–65535 端口（不填主机就是留空占位）") }
            return
        }
        val priority = editing.priority.toIntOrNull() ?: 0
        val profile = ProxyProfile(
            id = editing.id,
            name = name,
            type = editing.type,
            host = host,
            port = if (host.isBlank()) 0 else port,
            username = editing.username.trim(),
            password = editing.password,
            scope = editing.scope,
            priority = priority,
            enabled = editing.enabled,
        )
        val profiles = state.profiles.toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) profiles[index] = profile else profiles += profile
        repository.saveAll(profiles)
        ProxyRouter.invalidateCache()
        state = loadState().copy(feedback = "已保存 ${profile.name}")
    }

    fun delete(id: String) {
        val profiles = state.profiles.filter { it.id != id }
        repository.saveAll(profiles)
        if (state.manualId == id) {
            repository.setManualProfileId(null)
            repository.setMode(ProxyMode.AUTO)
        }
        ProxyRouter.invalidateCache()
        state = loadState().copy(feedback = "已删除")
    }

    fun setEnabled(id: String, enabled: Boolean) {
        repository.saveAll(state.profiles.map { if (it.id == id) it.copy(enabled = enabled) else it })
        ProxyRouter.invalidateCache()
        refresh()
    }

    /** 手动锁定一条；再点一次回到自动。 */
    fun toggleManualLock(id: String) {
        if (state.mode == ProxyMode.MANUAL && state.manualId == id) {
            repository.setManualProfileId(null)
            repository.setMode(ProxyMode.AUTO)
        } else {
            repository.setManualProfileId(id)
            repository.setMode(ProxyMode.MANUAL)
        }
        ProxyRouter.invalidateCache()
        refresh()
    }

    fun setFailoverDirect(value: Boolean) {
        repository.setFailoverDirect(value)
        ProxyRouter.invalidateCache()
        refresh()
    }

    /**
     * 连通性测试：先按类型做握手探测，再经该代理实际打开一次 linux.sb——
     * 只握手不通流量（例如 57081）时握手也会“连通”，必须以真实请求为准；不改配置。
     */
    fun test(id: String) {
        val profile = state.profiles.firstOrNull { it.id == id } ?: return
        if (!profile.isConfigured) {
            update { copy(testResults = testResults + (id to "未填好 host/端口，先填写再测")) }
            return
        }
        update { copy(testingId = id) }
        viewModelScope.launch(Dispatchers.IO) {
            val handshakeOk = when (profile.type) {
                ProxyType.SOCKS5 -> LocalProxyProbe.socks5(profile.host, profile.port, timeoutMs = 1500)
                else -> LocalProxyProbe.http(profile.host, profile.port, timeoutMs = 1500)
            }
            val result = when {
                !handshakeOk -> "连不通，请检查地址、端口和网络"
                realRequestOk(profile) -> "可用"
                else -> "握手通，但打不开 linux.sb（出口不通）"
            }
            update {
                copy(
                    testingId = null,
                    testResults = testResults + (id to result),
                )
            }
        }
    }

    /** 经指定代理实际请求一次 https://linux.sb/，2xx/3xx 才算可用。 */
    private fun realRequestOk(profile: ProxyProfile): Boolean = runCatching {
        val proxyType = if (profile.type == ProxyType.SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val client = OkHttpClient.Builder()
            .proxy(Proxy(proxyType, InetSocketAddress.createUnresolved(profile.host, profile.port)))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url("https://linux.sb/").build()
        client.newCall(request).execute().use { it.code in 200..399 }
    }.getOrDefault(false)

    private fun update(change: ProxyProfilesUiState.() -> ProxyProfilesUiState) {
        state = state.change()
    }
}

class ProxyProfilesViewModelFactory(
    private val repository: ProxyProfileRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProxyProfilesViewModel(repository) as T
}
