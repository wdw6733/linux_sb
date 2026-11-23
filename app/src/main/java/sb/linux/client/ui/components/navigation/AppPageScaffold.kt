package sb.linux.client.ui.components.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import sb.linux.client.ui.theme.GlassScroll
import sb.linux.client.ui.theme.GlassSignalLine
import sb.linux.client.ui.theme.LocalGlassBackdrop
import sb.linux.client.ui.theme.glassBar
import sb.linux.client.ui.theme.LocalAppSizes

/**
 * 二级页面外壳：标题、返回、actions、底栏与内容 inset。
 *
 * 约定（UI_DEVELOPMENT_GUIDE 5.2）：
 * - 不接收 `NavHostController`，返回动作由 Route / 适配层以 `onBack` 传入；
 *   `onBack` 传 `null` 表示该页当前不可返回（例如按状态条件显示返回的页面），
 *   此时不渲染返回图标，顶栏标题随之左移；
 * - 需要非返回图标（抽屉菜单、自定义无障碍描述）时用 [navigationIcon] 覆盖；
 * - 标题默认单行省略；需要头像 + 文字等组合标题时用 [titleContent]；
 * - 不强制滚动，内容自己决定普通 `Column` 还是 `LazyColumn` 消费 [PaddingValues]，
 *   避免外壳套滚动导致列表嵌套或恢复位置丢失；
 * - 底栏只给需要常驻输入区的页面（如私信会话）用，普通页面留空；
 * - 顶栏标题、返回图标与无障碍描述由此处统一，页面不再各拼一份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    primaryPage: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    ApplyDialogStatusBarPreference()
    Scaffold(
        modifier = modifier,
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                navigationIcon = navigationIcon,
                titleContent = titleContent,
                actions = actions,
                primaryPage = primaryPage,
                // 1.2.3 热修复：二级页顶栏在 NavHost 内容层内部，drawBackdrop 会在
                // 背板纹理正在录制时采样同一块纹理，Android 16 上直接闪退。
                // 先关掉二级页的层内玻璃（回到 1.2.0 的半透明外观），首页 overlay
                // 顶栏是背板兄弟，不受影响。
                glass = false,
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/**
 * 全应用统一顶栏：根页面（可内嵌在自有 Scaffold）与二级页面共用。
 *
 * - 高度取 [LocalAppSizes] 的 `topBarHeight`，避免各页 56/64dp 混用；
 * - [onBack] 非空时渲染标准返回；[navigationIcon] 非空时优先渲染自定义图标；
 * - [windowInsets] 供已消费状态栏的嵌套外壳传 `WindowInsets(0, 0, 0, 0)`；
 * - 标题单行省略；组合标题（头像 + 名称）用 [titleContent]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    primaryPage: Boolean = false,
    /** 首页 overlay 自带玻璃时传 false，本栏只留透明容器，不再叠加模糊与信号线。 */
    glass: Boolean = true,
    /** 滚动驱动的玻璃进度（0→1）；不传时读全局 [GlassScroll]（二级页默认 0）。 */
    glassProgress: Float? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val progress = (glassProgress ?: GlassScroll.progress).coerceIn(0f, 1f)
    Column(modifier) {
        TopAppBar(
            modifier = Modifier.heightIn(min = LocalAppSizes.current.topBarHeight)
                .then(if (glass) Modifier.glassBar(
                    backdrop = LocalGlassBackdrop.current,
                    alpha = 0.45f + progress * 0.25f,
                    surface = scheme.surfaceContainer,
                ) else Modifier),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            windowInsets = windowInsets,
            title = {
                if (titleContent != null) {
                    titleContent()
                } else if (primaryPage) {
                    AppPageTitle(title)
                } else {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                when {
                    navigationIcon != null -> navigationIcon()
                    onBack != null -> AppBackButton(onBack)
                }
            },
            actions = actions,
        )
        // 信号细线：随滚动进度生长，2dp accent；无滚动时不画。
        if (glass) GlassSignalLine(progress)
    }
}
