package sb.linux.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import sb.linux.client.domain.network.ProxyMode
import sb.linux.client.domain.network.ProxyProfile
import sb.linux.client.domain.network.ProxyScope
import sb.linux.client.domain.network.ProxyType
import sb.linux.client.ui.feature.network.ProxyProfileEditState
import sb.linux.client.ui.feature.network.ProxyProfilesViewModel
import sb.linux.client.ui.feature.network.ProxyProfilesViewModelFactory
import sb.linux.client.ui.patterns.settings.SettingsActionRow
import sb.linux.client.ui.patterns.settings.SettingsCheckRow
import sb.linux.client.ui.patterns.settings.SettingsChoiceRow
import sb.linux.client.ui.session.Session

/**
 * 代理列表：增删改多条 HTTP/HTTPS/SOCKS5 代理、连通性测试、手动锁定。
 * 所有代理信息都留位置给使用者自己填，不编造：
 * 内网那条默认填了 192.168.124.250:57081 作可改的示例值（UI 上写明可改），
 * 外网那条默认 host 留空，等使用者填自己的域名代理。
 * 密码按项目现有惯例存 SharedPreferences 明文，界面上有提示。
 */
@Composable
internal fun ProxyProfilesScreen(session: Session, nav: NavHostController) {
    val factory = remember(session.proxyProfileRepository) {
        ProxyProfilesViewModelFactory(session.proxyProfileRepository)
    }
    val viewModel: ProxyProfilesViewModel = viewModel(factory = factory)
    val state = viewModel.state
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            session.showToast(it)
            viewModel.consumeFeedback()
        }
    }
    SettingsPage("代理列表", nav) {
        GroupCard {
            Text("多条代理", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "每条代理都要填好主机和端口才能用；留空的就是占位，切换时会被跳过。" +
                    "内网那条默认填了 192.168.124.250:57081 作示例，可以改成你自己的；" +
                    "外网那条默认是空的，填你自己的域名代理地址。用户名/密码只在需要认证的代理上填。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "密码保存在本机设置里（明文），备份文件不要发给别人。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.mode == ProxyMode.MANUAL) {
                val locked = state.profiles.firstOrNull { it.id == state.manualId }
                Text(
                    "当前手动锁定：${locked?.name ?: "（锁定的代理已被删除）"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SettingsActionRow("添加代理", viewModel::startAdd)
            if (state.profiles.isEmpty()) {
                Text(
                    "还没有代理，点“添加代理”新建一条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.profiles.forEach { profile ->
                ProxyProfileRow(
                    profile = profile,
                    locked = state.mode == ProxyMode.MANUAL && state.manualId == profile.id,
                    testing = state.testingId == profile.id,
                    testResult = state.testResults[profile.id],
                    onToggleEnabled = { viewModel.setEnabled(profile.id, it) },
                    onToggleLock = { viewModel.toggleManualLock(profile.id) },
                    onTest = { viewModel.test(profile.id) },
                    onEdit = { viewModel.startEdit(profile) },
                    onDelete = { viewModel.delete(profile.id) },
                )
            }
        }
        GroupCard {
            SettingsCheckRow(
                "候选耗尽时直连",
                checked = state.failoverDirect,
                onCheckedChange = viewModel::setFailoverDirect,
                subtitle = "默认关闭：代理都连不上时请求直接失败，不悄悄直连",
            )
        }
    }
    state.editing?.let { editing ->
        ProxyProfileEditDialog(
            editing = editing,
            error = state.editError,
            onChange = viewModel::updateEdit,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::cancelEdit,
        )
    }
}

@Composable
private fun ProxyProfileRow(
    profile: ProxyProfile,
    locked: Boolean,
    testing: Boolean,
    testResult: String?,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleLock: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name + if (locked) "（已锁定）" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${profile.type}  ${profile.displayAddress}  ·  " +
                        when (profile.scope) {
                            ProxyScope.LAN_ONLY -> "仅内网"
                            ProxyScope.WAN_ONLY -> "仅外网"
                            ProxyScope.ALL -> "全部环境"
                        } + "  ·  优先级 ${profile.priority}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!profile.isConfigured) {
                    Text(
                        "占位：还没填主机/端口，切换时会被跳过",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = profile.enabled, onCheckedChange = onToggleEnabled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onTest, enabled = !testing) { Text(if (testing) "测试中…" else "测试") }
            TextButton(onClick = onToggleLock) { Text(if (locked) "取消锁定" else "锁定") }
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun ProxyProfileEditDialog(
    editing: ProxyProfileEditState,
    error: String?,
    onChange: (ProxyProfileEditState.() -> ProxyProfileEditState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing.name.isBlank()) "添加代理" else "编辑代理") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsTextField(
                    value = editing.name,
                    onValueChange = { onChange { copy(name = it) } },
                    label = { Text("名称") },
                    placeholder = { Text("例如：家里 SOCKS5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsChoiceRow(
                    "类型",
                    listOf(ProxyType.HTTP to "HTTP", ProxyType.HTTPS to "HTTPS", ProxyType.SOCKS5 to "SOCKS5"),
                    editing.type,
                    { onChange { copy(type = it) } },
                )
                SettingsTextField(
                    value = editing.host,
                    onValueChange = { onChange { copy(host = it) } },
                    label = { Text("主机") },
                    placeholder = { Text("内网示例 192.168.124.250，外网填域名") },
                    description = "可填可改：内网示例值只是默认值，不是写死的地址",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsTextField(
                    value = editing.port,
                    onValueChange = { onChange { copy(port = it.filter(Char::isDigit).take(5)) } },
                    label = { Text("端口") },
                    placeholder = { Text("内网示例 57081") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsTextField(
                    value = editing.username,
                    onValueChange = { onChange { copy(username = it) } },
                    label = { Text("用户名（可选）") },
                    placeholder = { Text("无认证可不填") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsTextField(
                    value = editing.password,
                    onValueChange = { onChange { copy(password = it) } },
                    label = { Text("密码（可选）") },
                    placeholder = { Text("无认证可不填") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsChoiceRow(
                    "适用环境",
                    listOf(
                        ProxyScope.LAN_ONLY to "仅内网",
                        ProxyScope.WAN_ONLY to "仅外网",
                        ProxyScope.ALL to "全部环境",
                    ),
                    editing.scope,
                    { onChange { copy(scope = it) } },
                )
                SettingsTextField(
                    value = editing.priority,
                    onValueChange = { onChange { copy(priority = it.filter { c -> c.isDigit() }.take(3)) } },
                    label = { Text("优先级") },
                    description = "数字越小越先用",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = editing.enabled, onCheckedChange = { onChange { copy(enabled = it) } })
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
