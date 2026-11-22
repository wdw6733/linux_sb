package sb.linux.client.ui.feature.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sb.linux.client.data.ProxyRouter
import sb.linux.client.domain.network.ProxyDetectionRepository
import sb.linux.client.domain.network.ProxyEnvironment
import sb.linux.client.domain.network.ProxyMode
import sb.linux.client.domain.network.ProxyProfile
import sb.linux.client.domain.network.ProxyProfileRepository
import sb.linux.client.domain.network.ProxyScope
import sb.linux.client.domain.network.ProxySettingsRepository
import sb.linux.client.domain.network.ProxyType

/**
 * 网络设置：模式（自动/手动锁定/直连）+ 当前环境/生效 profile/探测结果。
 * 单代理表单已移到代理列表（ProxyProfilesScreen）；这里只做模式切换、
 * 环境状态和重新检测（发现的代理进候选池，不覆盖已有配置）。
 */
data class NetworkSettingsUiState(
    val mode: ProxyMode,
    val environment: ProxyEnvironment?,
    val activeProfile: ProxyProfile?,
    val profiles: List<ProxyProfile>,
    val failoverDirect: Boolean,
    val detecting: Boolean = false,
    val proxyError: String? = null,
    val feedback: String? = null,
)

class NetworkSettingsViewModel(
    private val settingsRepository: ProxySettingsRepository,
    private val detectionRepository: ProxyDetectionRepository,
    private val profileRepository: ProxyProfileRepository,
    private val context: Context,
) : ViewModel() {
    var state by mutableStateOf(loadState())
        private set
    private var detectionJob: Job? = null
    private var environmentJob: Job? = null

    init {
        refreshEnvironment()
    }

    private fun loadState(): NetworkSettingsUiState {
        val repo = profileRepository
        return NetworkSettingsUiState(
            mode = repo.mode(),
            environment = null,
            activeProfile = null,
            profiles = repo.profiles(),
            failoverDirect = repo.failoverDirect(),
        )
    }

    /** 重新探测内外网环境 + 生效 profile（IO 线程做 800ms 握手，不堵 UI）。 */
    fun refreshEnvironment() {
        environmentJob?.cancel()
        environmentJob = viewModelScope.launch(Dispatchers.IO) {
            val environment = ProxyRouter.environment(context)
            val active = ProxyRouter.resolveActive(context)
            update {
                copy(
                    environment = environment,
                    activeProfile = active,
                    profiles = profileRepository.profiles(),
                    mode = profileRepository.mode(),
                    failoverDirect = profileRepository.failoverDirect(),
                )
            }
        }
    }

    fun setMode(mode: ProxyMode) {
        profileRepository.setMode(mode)
        if (mode != ProxyMode.MANUAL) profileRepository.setManualProfileId(null)
        ProxyRouter.invalidateCache()
        update { copy(mode = mode, feedback = null) }
        refreshEnvironment()
    }

    fun setFailoverDirect(value: Boolean) {
        profileRepository.setFailoverDirect(value)
        ProxyRouter.invalidateCache()
        update { copy(failoverDirect = value) }
    }

    fun consumeFeedback() = update { copy(feedback = null) }

    fun reset() {
        detectionJob?.cancel()
        settingsRepository.resetNetworkSettings()
        profileRepository.reset()
        ProxyRouter.invalidateCache()
        ProxyRouter.clearFailures()
        state = loadState().copy(feedback = "已重置网络设置")
        refreshEnvironment()
    }

    /**
     * 重新检测本地代理：发现的只进候选池（AppProxyDiscovery），不覆盖已有配置；
     * 这里只是等它跑完后刷新状态。
     */
    fun detect() {
        detectionJob?.cancel()
        update { copy(detecting = true, proxyError = null, feedback = null) }
        detectionJob = viewModelScope.launch {
            try {
                val found = detectionRepository.detect(null)
                // 发现逻辑在 AppProxyDiscovery.refresh 里已把结果写进候选池；
                // 这里的 detect 只做一次本地端口扫描兜底。
                if (found == null) {
                    update { copy(proxyError = "未检测到本地 HTTP 或 SOCKS5 代理，请确认代理软件已开启本地监听") }
                } else {
                    val profiles = profileRepository.profiles()
                    if (profiles.none { it.host == found.host && it.port == found.port }) {
                        profileRepository.saveAll(
                            profiles + ProxyProfile(
                                name = "自动发现（${found.host}:${found.port}）",
                                type = if (found.type == 2) ProxyType.SOCKS5 else ProxyType.HTTP,
                                host = found.host,
                                port = found.port,
                                scope = ProxyScope.ALL,
                                priority = 10,
                            ),
                        )
                        ProxyRouter.invalidateCache()
                    }
                    update {
                        copy(feedback = "已检测到 ${if (found.type == 2) "SOCKS5" else "HTTP"} 代理 ${found.host}:${found.port}，已加入候选")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                update { copy(proxyError = "未检测到本地 HTTP 或 SOCKS5 代理，请确认代理软件已开启本地监听") }
            } finally {
                update { copy(detecting = false) }
                refreshEnvironment()
            }
        }
    }

    fun cancelDetection() {
        detectionJob?.cancel()
        environmentJob?.cancel()
    }

    private fun update(change: NetworkSettingsUiState.() -> NetworkSettingsUiState) {
        state = state.change()
    }
}

class NetworkSettingsViewModelFactory(
    private val settings: ProxySettingsRepository,
    private val detection: ProxyDetectionRepository,
    private val profiles: ProxyProfileRepository,
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        NetworkSettingsViewModel(settings, detection, profiles, context) as T
}
