package sb.linux.client.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/**
 * 动态毛玻璃（unslop 亮点，只两处：顶栏、底栏/rail；帖子卡片等内容面不用玻璃）。
 *
 * 接线（Kyant0 Backdrop 2.0.0，官方 glass-bottom-bar 教程模式）：
 * 铁律：画玻璃的栏必须是被录层的兄弟，绝不能在被录层内部采样同一块背板——
 *   1.2.1 二级页顶栏就是这么在 Android 16 上闪退的（1.2.3 热修复关掉玻璃，
 *   1.2.4 按此接线恢复）。
 * - 外壳（AppRoot）建一个 [LayerBackdrop]，经 [LocalGlassBackdrop] 提供给底栏/rail；
 *   AppContent 把它挂到 NavHost 内容层上（`layerBackdrop`），底栏/rail 与内容层是兄弟。
 * - 每个顶栏自建局部背板：首页（HomeNavigationLayout）、帖子页（TopicScreenScaffold）、
 *   二级页（AppPageScaffold）各自在 Box 内建背板、把内容层挂上去，顶栏做 overlay 兄弟。
 * - 效果链固定顺序 colorFilter ⇒ blur ⇒ lens：先 vibrancy（饱和度 1.5，抵消模糊的灰化），
 *   再 blur。矩形栏不用 lens（lens 要从 CornerBasedShape 取圆角做 SDF 折射边，且需
 *   Android 13+ 的 AGSL；矩形栏无圆角可用）；depthEffect / chromaticAberration 随 lens
 *   一起关闭。highlight 只留库默认 1px 发丝线，shadow 关掉（无彩色阴影/发光）。
 * - 模糊半径不随滚动变，只动画 alpha/颜色：顶栏 24dp、底栏/rail 20dp。
 *   顶栏 alpha = 0.35 + progress × 0.35（静止透、滚动实），底栏 0.60、rail 0.62。
 * - 降级：无背板或 Android 12 以下时回退半透明表面；tint ≤ 0.01 时不画玻璃。
 * - 玻璃强度复用现有表面不透明度滑条（[LocalSurfaceOpacity]）。
 */
object GlassScroll {
    /** 0..1：有列表的页面写入（滚动 0→64dp），顶栏与壁纸视差读取；路由切换时外壳清零。 */
    var progress by mutableFloatStateOf(0f)
}

/** 当前生效的背板；null 时玻璃退化为半透明表面。 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 滚动 0→64dp 映射为 0→1 的玻璃进度；首项已滚出时直接为 1。 */
@Composable
fun LazyListState.glassProgress(): State<Float> {
    val density = LocalDensity.current
    return remember(this, density) {
        derivedStateOf {
            if (firstVisibleItemIndex > 0) 1f
            else (firstVisibleItemScrollOffset / with(density) { 64.dp.toPx() }).coerceIn(0f, 1f)
        }
    }
}

/**
 * 玻璃栏：有背板且 alpha>0 时画模糊 + 表面色；否则按 alpha 画半透明表面；
 * alpha=0 时完全透明（无滚动的顶栏露壁纸/首屏）。
 */
@Composable
fun Modifier.glassBar(
    backdrop: Backdrop?,
    alpha: Float,
    surface: Color,
    shape: Shape = RectangleShape,
    blurRadius: Dp = 24.dp,
): Modifier {
    val strength = LocalSurfaceOpacity.current
    val tintAlpha = (alpha * strength).coerceIn(0f, 1f)
    if (tintAlpha <= 0.01f) return this
    if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
        return this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { vibrancy(); blur(radiusPx) },
            highlight = { Highlight.Default },
            shadow = null,
            onDrawSurface = { drawRect(surface.copy(alpha = tintAlpha)) },
        )
    }
    return this.background(surface.copy(alpha = tintAlpha), shape)
}

/** 顶栏底部 2dp accent 信号细线：宽度 = 同一滚动进度；无滚动时不画。 */
@Composable
fun GlassSignalLine(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    Box(
        modifier
            .fillMaxWidth(progress.coerceIn(0f, 1f))
            .height(2.dp)
            .background(MaterialTheme.colorScheme.primary)
    )
}
