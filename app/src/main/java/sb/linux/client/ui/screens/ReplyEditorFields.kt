package sb.linux.client.ui.screens
import sb.linux.client.ui.session.Session


import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.foundation.text.selection.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import sb.linux.client.ui.*
import sb.linux.client.ui.theme.*
import sb.linux.client.ui.content.*
import sb.linux.client.ui.patterns.topic.*
import sb.linux.client.ui.patterns.user.*
import sb.linux.client.ui.components.feedback.*
import sb.linux.client.ui.components.layout.*
import sb.linux.client.ui.components.navigation.*

import sb.linux.client.domain.account.LoginVerification
import kotlinx.coroutines.delay
/** Markdown 工具栏（横向滚动；私信等场景隐藏），onInsert(before, after, placeholder) */
@Composable
internal fun ReplyToolbar(
    onInsert: (String, String, String) -> Unit,
    onUploadImage: (() -> Unit)? = null,
    imageUploadBusy: Boolean = false,
    onAdvanced: ((String) -> Unit)? = null,
) {
    sb.linux.client.ui.content.MarkdownEditorTools(
        onInsert = onInsert, onUpload = { onUploadImage?.invoke() ?: onInsert("![", "](https://)", "图片描述") },
        uploading = imageUploadBusy,
        onAdvanced = onAdvanced, topic = false,
    )
}

/** 按源站协议显示 PoW、CAP 或旧版问答验证。
 * captcha==null 不再静默 return：打开时已自动刷新过一次，仍为 null
 * 说明验证组件没加载出来——显示缺失提示 + 重新加载 + 全屏源站编辑器兜底，
 * 提交在此时不放行（见 TopicReplyDialog.canSubmit）。 */
@Composable
internal fun ReplyCaptchaField(
    session: Session,
    captcha: LoginVerification?,
    answer: String,
    onAnswerChange: (String) -> Unit,
    onRefresh: (() -> Unit)?,
    topPadding: androidx.compose.ui.unit.Dp,
    revision: Int,
    onReloadCap: () -> Unit,
    captchaMissing: Boolean = false,
    onOpenSourceEditor: (() -> Unit)? = null,
) {
    if (captcha == null) {
        Spacer(Modifier.height(topPadding))
        if (captchaMissing) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "未加载到人机验证",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "回复需要先通过人机验证，但验证组件没有加载出来。可以重新加载，或去全屏源站编辑器里回复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onRefresh != null) {
                        TextButton(onClick = onRefresh) { Text("重新加载") }
                    }
                    if (onOpenSourceEditor != null) {
                        TextButton(onClick = onOpenSourceEditor) { Text("在全屏源站编辑器中回复") }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                sb.linux.client.ui.components.feedback.AppInlineLoading(size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "正在加载人机验证…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Spacer(Modifier.height(topPadding))
    when (captcha) {
        is LoginVerification.Pow -> {
            var showFullscreen by remember(captcha, revision) { mutableStateOf(false) }
            val verified = answer.isNotBlank()
            TextButton(onClick = { showFullscreen = true }) {
                Text(if (verified) "人机验证已完成，点此重新验证" else "打开人机验证")
            }
            if (showFullscreen) {
                ReplyVerificationFullscreenDialog(
                    captcha = captcha,
                    revision = revision,
                    gateway = session.accountVerificationGateway,
                    onToken = { onAnswerChange(it) },
                    onDismiss = { showFullscreen = false },
                    onReload = onReloadCap,
                )
            }
        }
        is sb.linux.client.domain.account.LoginVerification.Native -> OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            label = { Text("人机验证：${captcha.question}") },
            singleLine = true,
            trailingIcon = if (onRefresh != null) {
                {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, "换一题")
                    }
                }
            } else null,
        )
        is sb.linux.client.domain.account.LoginVerification.Cap -> {
            var capError by remember(captcha, revision) { mutableStateOf<String?>(null) }
            var showFullscreen by remember(captcha, revision) { mutableStateOf(false) }
            val verified = answer.isNotBlank()
            TextButton(onClick = { showFullscreen = true }) {
                Text(if (verified) "人机验证已完成，点此重新验证" else "打开人机验证")
            }
            capError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onReloadCap) { Text("重新加载验证码") }
            }
            if (showFullscreen) {
                ReplyVerificationFullscreenDialog(
                    captcha = captcha,
                    revision = revision,
                    gateway = session.accountVerificationGateway,
                    onToken = { onAnswerChange(it) },
                    onError = { capError = it; onAnswerChange("") },
                    onDismiss = { showFullscreen = false },
                    onReload = onReloadCap,
                )
            }
        }
    }
}

/**
 * 回复验证码的全屏 Dialog：复用 VerificationDialog 的全屏样式
 * （usePlatformDefaultWidth=false），JS + domStorageEnabled 由内嵌的
 * Cap/Pow 组件自己开启；成功回调走现有的 LsbCapStatus.token() /
 * PowStatusBridge（组件内部已接好，这里只收 token）。
 * Cap 12s、Pow 10s 内无结果给超时文案 + 重新加载按钮，不无限空转。
 */
@Composable
private fun ReplyVerificationFullscreenDialog(
    captcha: LoginVerification,
    revision: Int,
    gateway: sb.linux.client.platform.verification.AccountVerificationGateway,
    onToken: (String) -> Unit,
    onError: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
) {
    var completed by remember(captcha, revision) { mutableStateOf(false) }
    var dialogError by remember(captcha, revision) { mutableStateOf<String?>(null) }
    var reloadTick by remember(captcha, revision) { mutableIntStateOf(0) }
    val latestToken by rememberUpdatedState(onToken)
    val latestError by rememberUpdatedState(onError)
    // Cap 组件内部已有 12s 超时；Pow 没有，这里统一再兜 10s，超时只给文案不关窗。
    LaunchedEffect(captcha, revision, reloadTick) {
        delay(10_000)
        if (!completed) dialogError = "人机验证加载超时，请重试"
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "人机验证",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "请在下方完成验证，通过后自动填入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    key(revision, reloadTick) {
                        when (captcha) {
                            is LoginVerification.Cap -> CapVerificationWidget(
                                verification = captcha,
                                revision = revision,
                                gateway = gateway,
                                onToken = { token ->
                                    completed = true
                                    dialogError = null
                                    latestToken(token)
                                    onDismiss()
                                },
                                onStatus = {},
                                onError = { message ->
                                    dialogError = message
                                    latestError?.invoke(message)
                                },
                                boxModifier = Modifier.fillMaxSize(),
                            )
                            is LoginVerification.Pow -> PowVerificationWidget(
                                captcha,
                                gateway,
                                { proof ->
                                    if (proof.isNotBlank()) {
                                        completed = true
                                        dialogError = null
                                        latestToken(proof)
                                        onDismiss()
                                    }
                                },
                                null,
                                revision,
                            )
                            else -> Unit
                        }
                    }
                }
                dialogError?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        dialogError = null
                        completed = false
                        reloadTick++
                        onReload()
                    }) { Text("重新加载") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}
