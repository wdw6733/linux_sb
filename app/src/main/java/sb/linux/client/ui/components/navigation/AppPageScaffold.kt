package sb.linux.client.ui.components.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 每页自建局部背板（与首页/帖子页同一接法）：只录内层 Scaffold，顶栏是外层 Box 里的
    // overlay 兄弟。绝不能再用外壳背板——顶栏在 NavHost 层内，1.2.1 就是这么闪退的。
    val pageBackdropBg = scheme.background
    val pageBackdrop = rememberLayerBackdrop { drawRect(pageBackdropBg); drawContent() }
    // 顶栏高：初值 64dp（LocalAppSizes.topBarHeight），实测到更大值时跟进
    // （超大字体下标题可能撑高顶栏；旧 Scaffold 是实测高度，新结构必须同样实测）。
    var topBarHeight by remember { mutableStateOf(LocalAppSizes.current.topBarHeight) }
    val progress = GlassScroll.progress.coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // 注意：bottomBar 也在被录层内——它现在不用玻璃所以安全；以后若给底栏加 glassBar，
        // 必须用外壳背板（LocalGlassBackdrop）且保证底栏是 NavHost 层的兄弟，绝不能用 pageBackdrop。
        Scaffold(
            modifier = modifier.fillMaxSize().layerBackdrop(pageBackdrop),
            // 顶栏移到外层 overlay，内层不再占顶栏位；内容顶垫 = 状态栏 + 顶栏 + 2dp 信号线，
            // 与旧 Scaffold（顶栏槽实测高）完全一致，各页首项不会被顶栏盖住。
            topBar = {},
            bottomBar = bottomBar,
            content = { padding ->
                val adjusted = PaddingValues(
                    top = padding.calculateTopPadding() + topBarHeight + 2.dp,
                    bottom = padding.calculateBottomPadding(),
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                )
                content(adjusted)
            },
        )
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .glassBar(
                    backdrop = pageBackdrop,
                    alpha = 0.35f + progress * 0.35f,
                    surface = scheme.surfaceContainer,
                    blurRadius = 24.dp,
                )
                .onSizeChanged { size ->
                    val measured = with(density) { size.height.toDp() } - statusBarTop - 2.dp
                    val grown = measured.coerceAtLeast(LocalAppSizes.current.topBarHeight)
                    if (grown != topBarHeight) topBarHeight = grown
                },
        ) {
            // 本栏只留透明容器，不叠加第二层玻璃；玻璃在外层 Column 上。
            // windowInsets 保持默认（状态栏）：外层 Box 没有 statusBarsPadding，
            // 状态栏高度由 TopAppBar 自身消化，与内容顶垫里的状态栏部分对齐。
            AppTopBar(
                title = title,
                onBack = onBack,
                navigationIcon = navigationIcon,
                titleContent = titleContent,
                actions = actions,
                primaryPage = primaryPage,
                glass = false,
            )
            GlassSignalLine(progress)
        }
    }
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
                    alpha = 0.35f + progress * 0.35f,
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
