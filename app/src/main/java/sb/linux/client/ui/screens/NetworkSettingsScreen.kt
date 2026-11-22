package sb.linux.client.ui.screens
import sb.linux.client.ui.patterns.settings.SettingsActionRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import sb.linux.client.ui.session.Session
import sb.linux.client.domain.network.ProxyMode
import sb.linux.client.ui.feature.network.NetworkSettingsViewModel
import sb.linux.client.ui.feature.network.NetworkSettingsViewModelFactory
import sb.linux.client.ui.theme.LocalAppSpacing
import sb.linux.client.ui.patterns.settings.SettingsCheckRow
import sb.linux.client.ui.patterns.settings.SettingsChoiceRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkSettingsDetailScreen(session: Session, nav: NavHostController, section: String) {
    val appContext = LocalContext.current.applicationContext
    val factory = remember(session.proxySettingsRepository, session.proxyDetectionRepository, session.proxyProfileRepository) {
        NetworkSettingsViewModelFactory(
            session.proxySettingsRepository,
            session.proxyDetectionRepository,
            session.proxyProfileRepository,
            appContext,
        )
    }
    val viewModel: NetworkSettingsViewModel = viewModel(factory = factory)
    val state = viewModel.state
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            session.showToast(it)
            viewModel.consumeFeedback()
        }
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelDetection() }
    }

    val title = when (section) { "proxy" -> "应用代理"; "dns" -> "加密 DNS"; else -> "链接设置" }
    SettingsPage(title, nav, onBack = { nav.popBackStack() }) {
        if (section == "proxy") {
            GroupCard {
                Text("应用代理", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "自动模式按环境切换：到家连上 WiFi 且本机在 192.168.x、内网代理可连通时走内网代理，否则走外网代理。代理生效时，应用联网和网页浏览优先使用代理，DoH 暂停；关闭后恢复已保存的 DoH 设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 当前环境 / 生效代理状态区
                val environment = state.environment
                Text(
                    if (environment == null) "正在探测内外网环境…"
                    else "当前环境：${if (environment.isLan) "内网" else "外网"}（${environment.reason}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        state.mode == ProxyMode.OFF -> "生效代理：直连（代理已关闭）"
                        state.activeProfile != null -> "生效代理：${state.activeProfile.name}（${state.activeProfile.type} ${state.activeProfile.displayAddress}）"
                        state.mode == ProxyMode.MANUAL -> "生效代理：手动锁定的代理未填好或已停用"
                        else -> "生效代理：无可用代理（直连已关闭，请求会失败；请去代理列表填好一条）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsChoiceRow(
                    "代理模式",
                    listOf(0 to "自动", 1 to "手动锁定", 2 to "直连"),
                    when (state.mode) { ProxyMode.AUTO -> 0; ProxyMode.MANUAL -> 1; ProxyMode.OFF -> 2 },
                    { index ->
                        viewModel.setMode(when (index) { 1 -> ProxyMode.MANUAL; 2 -> ProxyMode.OFF; else -> ProxyMode.AUTO })
                    },
                )
                if (state.mode == ProxyMode.MANUAL && state.activeProfile == null) {
                    Text(
                        "手动锁定需在代理列表里点一条代理的“锁定”；没锁定时不走代理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsActionRow(
                    "代理列表",
                    { nav.navigate("proxyProfiles") },
                    subtitle = "添加、编辑多条 HTTP / HTTPS / SOCKS5 代理，填内网和外网地址",
                )
                SettingsActionRow(
                    "重新检测本地代理",
                    viewModel::detect,
                    enabled = !state.detecting,
                    subtitle = if (state.detecting) "正在检测…" else "发现的代理只加入候选，不覆盖已有配置",
                )
                SettingsActionRow("重新探测内外网环境", viewModel::refreshEnvironment,
                    subtitle = "切网后手动刷新一次生效代理")
                SettingsCheckRow(
                    "候选耗尽时直连",
                    checked = state.failoverDirect,
                    onCheckedChange = viewModel::setFailoverDirect,
                    subtitle = "默认关闭：代理都连不上时请求直接失败，不悄悄直连",
                )
                state.proxyError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (section == "dns") DohSettingsContent(session)
        if (section == "links") LinkSettingsContent(session)
    }
}
