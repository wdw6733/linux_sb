package sb.linux.client.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sb.linux.client.ui.theme.LiquidGlassBarMargin
import sb.linux.client.ui.theme.LocalGlassBackdrop
import sb.linux.client.ui.theme.glassBar
import sb.linux.client.ui.theme.LocalAppSizes
import sb.linux.client.ui.theme.LocalAppSpacing

internal val LocalSourceSidebarInRail = staticCompositionLocalOf { false }

/**
 * 主目的地导航（DESIGN_SYSTEM D06 `AppPrimaryNavigation`）。
 *
 * 手机使用标准 NavigationBar，宽窗使用同一目的地模型的 NavigationRail；
 * 仅有 2 个有效目的地时使用顶部 PrimaryTabRow（根内分区应改用分段控件）。
 * 所有形态共享选中态、标签与点击事件，不维护第二套导航数据。
 */
@Composable
internal fun AppBottomNavigation(
    destinations: List<AppDestination>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 浮动液态玻璃胶囊：边距加在根 Box（玻璃 NavigationBar 的父级）上，底边悬空 12dp；
    // 选中项 accent 细线在 Box 内，与栏的项目对齐。
    Box(Modifier.padding(
        start = LiquidGlassBarMargin,
        end = LiquidGlassBarMargin,
        bottom = LiquidGlassBarMargin,
    )) {
        NavigationBar(
            modifier = Modifier.glassBar(
                backdrop = LocalGlassBackdrop.current,
                alpha = 0.60f,
                surface = scheme.surfaceContainer,
                blurRadius = 20.dp,
            ),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            destinations.forEach { destination ->
                val selected = destination.id == selectedId
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination.id) },
                    icon = {
                        Icon(
                            if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    // 标签就是无障碍名称，图标不重复朗读。
                    label = { Text(destination.label) },
                    // 选中态：MD3 pill 改为表面台阶。
                    colors = NavigationBarItemDefaults.colors(indicatorColor = scheme.surfaceContainerHighest),
                )
            }
        }
        // 选中项顶部 2dp accent 细线（与顶栏信号细线同一语义）。
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            destinations.forEach { destination ->
                Box(Modifier.weight(1f).height(2.dp)) {
                    if (destination.id == selectedId) Box(Modifier.fillMaxSize().background(scheme.primary))
                }
            }
        }
    }
}

@Composable
internal fun AppNavigationRail(
    destinations: List<AppDestination>,
    selectedId: String,
    onSelect: (String) -> Unit,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    val sizes = LocalAppSizes.current
    val scheme = MaterialTheme.colorScheme
    // 浮动液态玻璃胶囊 rail：四边各留 12dp 悬空；NavigationRail 仍是 NavHost 内容层的兄弟。
    Box(Modifier.fillMaxHeight().padding(
        vertical = LiquidGlassBarMargin,
        horizontal = LiquidGlassBarMargin,
    )) {
        NavigationRail(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxHeight().width(sizes.railWidth)
                .glassBar(
                    backdrop = LocalGlassBackdrop.current,
                    alpha = 0.62f,
                    surface = scheme.surfaceContainer,
                    blurRadius = 20.dp,
                ),
            header = header,
        ) {
        // 宽窗账号入口固定在侧栏底部，其余目的地保留用户顺序。
        (destinations.filterNot { it.id == AppDestinations.me.id } +
            destinations.filter { it.id == AppDestinations.me.id }).forEach { destination ->
            if (destination.id == AppDestinations.me.id) Spacer(Modifier.weight(1f))
            val selected = destination.id == selectedId
            Box(Modifier.fillMaxWidth()) {
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelect(destination.id) },
                    icon = {
                        Icon(
                            if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                    // 选中态：MD3 pill 改为表面台阶。
                    colors = NavigationRailItemDefaults.colors(indicatorColor = scheme.surfaceContainerHighest),
                )
                // 选中项顶部 2dp accent 细线。
                if (selected) Box(
                    Modifier.align(Alignment.TopCenter).width(24.dp).height(2.dp)
                        .background(scheme.primary, RoundedCornerShape(1.dp))
                )
            }
        }
        }
    }
}

/** 只有 2 个有效根目的地时的兼容布局：顶部 PrimaryTabRow，取消底部导航。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopPrimaryTabs(
    destinations: List<AppDestination>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val spacing = LocalAppSpacing.current
    val selectedIndex = destinations.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        destinations.forEachIndexed { index, destination ->
            val selected = index == selectedIndex
            Tab(
                selected = selected,
                onClick = { onSelect(destination.id) },
                text = { Text(destination.label) },
                icon = {
                    Icon(
                        if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.padding(horizontal = spacing.inline),
            )
        }
    }
}
