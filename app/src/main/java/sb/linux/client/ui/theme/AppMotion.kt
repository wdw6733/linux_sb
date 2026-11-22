package sb.linux.client.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** 全应用唯一缓动（unslop）：cubic-bezier(0.22, 1, 0.36, 1)。 */
val UnslopEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** 动效时长（毫秒）：反馈 120–180，菜单 160–220，入场/页面 300–500，stagger 40–60 最多 6 项。 */
object UnslopMotion {
    const val FeedbackMs = 150
    const val MenuMs = 200
    const val EnterMs = 400
    const val StaggerMs = 50
    const val MaxStaggerItems = 6
}
