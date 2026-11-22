package sb.linux.client.ui.patterns.topic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sb.linux.client.domain.topic.TopicCard
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import sb.linux.client.ui.theme.LocalAppSizes
import sb.linux.client.ui.theme.LocalAppSpacing
import sb.linux.client.ui.theme.LocalSurfaceOpacity
import sb.linux.client.ui.theme.UnslopEasing
import sb.linux.client.ui.theme.UnslopMotion
import sb.linux.client.ui.theme.desaturate

/**
 * 帖子卡片（沿用 main 分支样式）：圆角卡片容器，左侧头像，中间标题与元信息，
 * 板块徽标固定在右上角。
 *
 * compact = 单列精简模式：隐藏头像与回复数。
 * onElementClick 非空时进入「预览改色」模式：点按各元素回调对应 [CardColorOverrides] key（标题不可改色）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicCardView(
    card: TopicCard,
    onClick: () -> Unit,
    onForumClick: (Long) -> Unit = {},
    compact: Boolean = false,
    views: Int = -1,
    showComments: Boolean = true,   // false 时不显示评论数（浏览历史只展示浏览时间）
    showForum: Boolean = true,      // false 时不显示板块徽标（浏览历史）
    onElementClick: ((key: String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    // 源站标题色保留在内容区，降饱和 20% 后再用。
    val titleColor = remember(card.titleColor) {
        runCatching { Color(android.graphics.Color.parseColor(card.titleColor)) }
            .getOrNull()?.takeIf { card.titleColor.isNotBlank() }?.let { desaturate(it) }
    }
    // 外观设置-实时预览的自定义元素颜色（未覆盖时跟随主题）
    val cardColors = LocalCardColorOverrides.current
    val tagColor = cardColors[CardColorOverrides.TAG]
    val bgOverride = cardColors[CardColorOverrides.BACKGROUND]
    val timeOverride = cardColors[CardColorOverrides.TIME]
    val userOverride = cardColors[CardColorOverrides.USER]
    val commentsOverride = cardColors[CardColorOverrides.COMMENTS]
    val forumOverride = cardColors[CardColorOverrides.FORUM]

    // 按压缩放 0.98（反馈时长 150ms，唯一缓动）。
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(UnslopMotion.FeedbackMs, easing = UnslopEasing),
        label = "cardPress",
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = (bgOverride ?: MaterialTheme.colorScheme.surfaceContainerLow)
            .copy(alpha = LocalSurfaceOpacity.current),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalAppSpacing.current.pageHorizontal, vertical = LocalAppSpacing.current.inline)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                interactionSource = pressSource,
                onClick = {
                    // 预览改色模式：点按卡片空白处改背景色；正常模式打开帖子
                    if (onElementClick != null) onElementClick(CardColorOverrides.BACKGROUND)
                    else onClick()
                },
                onLongClick = if (onElementClick == null) onLongClick else null,
            ),
    ) {
        if (sb.linux.client.ui.LocalTopicCardAvatarMetadataRow.current) {
            Column(Modifier.padding(LocalAppSpacing.current.contentGap)) {
                TopicCardTitle(card, titleColor, tagColor,
                    onElementClick?.let { { it(CardColorOverrides.TAG) } })
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) {
                        sb.linux.client.ui.patterns.user.UserAvatar(card.authorId, card.avatarUrl, LocalAppSizes.current.avatarReply.value.toInt(), online = card.online)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        TopicCardMetadata(card, showForum, showComments && !compact, onForumClick,
                            userOverride, timeOverride, commentsOverride, forumOverride, onElementClick, separateAuthor = true)
                    }
                }
            }
        } else Row(Modifier.padding(LocalAppSpacing.current.contentGap)) {
            if (!compact) {
                sb.linux.client.ui.patterns.user.UserAvatar(card.authorId, card.avatarUrl, LocalAppSizes.current.avatarReply.value.toInt(), online = card.online)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                TopicCardTitle(card, titleColor, tagColor,
                    onElementClick?.let { { it(CardColorOverrides.TAG) } })
                Spacer(Modifier.size(4.dp))
                TopicCardMetadata(card, showForum, showComments && !compact, onForumClick,
                    userOverride, timeOverride, commentsOverride, forumOverride, onElementClick)
            }
        }
    }
}
