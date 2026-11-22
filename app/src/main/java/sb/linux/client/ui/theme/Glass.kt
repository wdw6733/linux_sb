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
import com.kyant.backdrop.highlight.Highlight

/**
 * 动态毛玻璃（unslop 亮点，只两处：顶栏、底栏/rail；帖子卡片等内容面不用玻璃）。
 *
 * 接线（Kyant0 Backdrop 2.0.0，官方 glass-bottom-bar 教程模式）：
 * - 外壳（AppRoot）建一个 [LayerBackdrop]，经 [LocalGlassBackdrop] 提供给底栏/rail；
 *   AppContent 把它挂到 NavHost 内容层上（`layerBackdrop`），栏与内容层是兄弟，
 *   栏用 [glassBar] 画模糊后的背板。
 * - 首页/帖子页顶栏是内容上的 overlay，另在各自 Box 内建局部背板、挂到列表层上，
 *   同样是兄弟关系；二级页顶栏复用外壳背板（层内含顶栏自身上一帧，16dp 模糊 +
 *   0.82 表面色遮盖下只剩轻微雾感，见汇报）。
 * - 模糊半径固定 16dp，不随滚动变；只动画 alpha/颜色。矩形栏不用 lens
 *   （lens 要求 CornerBasedShape 且 Android 13+，矩形栏无圆角可用）；depthEffect /
 *   chromaticAberration 随 lens 一起关闭，即最小档。highlight 只留库默认 1px 发丝线，
 *   shadow 关掉（无彩色阴影/发光）。
 * - 降级：无背板或 Android 12 以下时回退半透明表面；无滚动（progress=0）时顶栏完全透明。
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
 * 玻璃栏：有背板且 alpha>0 时画 16dp 模糊 + 表面色；否则按 alpha 画半透明表面；
 * alpha=0 时完全透明（无滚动的顶栏露壁纸/首屏）。
 */
@Composable
fun Modifier.glassBar(
    backdrop: Backdrop?,
    alpha: Float,
    surface: Color,
    shape: Shape = RectangleShape,
    blurRadius: Dp = 16.dp,
): Modifier {
    val strength = LocalSurfaceOpacity.current
    val tintAlpha = (alpha * strength).coerceIn(0f, 1f)
    if (tintAlpha <= 0.01f) return this
    if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
        return this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(radiusPx) },
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
