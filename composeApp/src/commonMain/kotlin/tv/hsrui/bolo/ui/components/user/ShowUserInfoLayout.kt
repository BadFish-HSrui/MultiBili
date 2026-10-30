package tv.hsrui.bolo.ui.components.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.theme.BoloShapes

@Composable
internal fun ShowUserInfoLayout(
    face: String,
    name: String,
    sign: String,
    level: Int,
    levelString: String,
    isVip: Boolean,
    vipTypeString: String,
    modifier: Modifier = Modifier,
    shape: Shape = BoloShapes.List.Top,
    fixedHeight: Dp? = null,
    signMinLines: Int = 1,
    onClick: (() -> Unit)? = null,
    onUserClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = onUserClick,
    avatarBadgeText: String? = if (isVip) vipTypeString else null,
    isLive: Boolean = false,
    showLevel: Boolean = true,
    showStatistics: Boolean = true,
    descriptionContent: (@Composable ColumnScope.() -> Unit)? = null,
    nameTrailingContent: @Composable RowScope.() -> Unit = {},
    adaptiveNameDetails: Boolean = false,
    nameSuffixContent: @Composable RowScope.() -> Unit = {},
    statistics: @Composable RowScope.() -> Unit,
) {
    val userClickModifier = if (onUserClick == null) Modifier else Modifier.clickable(onClick = onUserClick)
    val avatarClickModifier = if (onAvatarClick == null) Modifier else Modifier.clickable(onClick = onAvatarClick)
    val levelContent: @Composable () -> Unit = {
        if (showLevel) {
            Surface(shape = CircleShape, color = BiliColor.getLevelColor(level)) {
                Text(
                    text = levelString,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
    val nameContent: @Composable (Modifier) -> Unit = { nameModifier ->
        Text(
            text = name,
            textAlign = TextAlign.Center,
            color = if (isVip) BiliColor.ThemeColor else LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = nameModifier.padding(horizontal = 4.dp).then(userClickModifier),
        )
    }
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fixedHeight != null) Modifier.weight(1f) else Modifier)
                .padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
        ) {
            Column(modifier = Modifier.width(64.dp)) {
                if (isLive) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Red,
                        modifier = Modifier
                            .width(64.dp)
                            .zIndex(1F)
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                // 只为上半部分占位，让标签中心与头像顶部对齐。
                                layout(placeable.width, placeable.height / 2) {
                                    placeable.placeRelative(0, 0)
                                }
                            },
                    ) {
                        Text(
                            text = "直播中",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                AsyncImage(
                    model = face,
                    contentDescription = "个人头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1F).clip(CircleShape).then(avatarClickModifier),
                )
                if (avatarBadgeText != null) {
                    Surface(
                        shape = CircleShape,
                        color = BiliColor.ThemeColor,
                        modifier = Modifier.width(64.dp),
                    ) {
                        Text(
                            text = avatarBadgeText,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1F).padding(start = 8.dp).wrapContentHeight()) {
                if (adaptiveNameDetails) {
                    Layout(
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            Box { levelContent() }
                            Box { nameContent(Modifier) }
                            Row(content = nameSuffixContent)
                            Row(content = nameTrailingContent)
                        },
                    ) { measurables, constraints ->
                        val width = constraints.maxWidth
                        val loose = constraints.copy(minWidth = 0, minHeight = 0)
                        val levelPlaceable = measurables[0].measure(loose)
                        val suffixPlaceable = measurables[2].measure(loose)
                        val trailingPlaceable = measurables[3].measure(loose)
                        val trailingGap = if (trailingPlaceable.width > 0) 4.dp.roundToPx() else 0
                        val detailsWidth = levelPlaceable.width + suffixPlaceable.width
                        val nameWidth = measurables[1].maxIntrinsicWidth(Constraints.Infinity)
                        val wrapDetails = detailsWidth + nameWidth + trailingPlaceable.width + trailingGap > width
                        val availableNameWidth = width - trailingPlaceable.width - trailingGap -
                            (if (wrapDetails) 0 else detailsWidth)
                        val namePlaceable = measurables[1].measure(
                            loose.copy(maxWidth = availableNameWidth.coerceAtLeast(0)),
                        )
                        val detailsHeight = maxOf(levelPlaceable.height, suffixPlaceable.height)
                        val firstRowHeight = maxOf(
                            namePlaceable.height,
                            trailingPlaceable.height,
                            if (wrapDetails) 0 else detailsHeight,
                        )
                        val rowGap = if (wrapDetails && detailsHeight > 0) 4.dp.roundToPx() else 0
                        val height = firstRowHeight + (if (wrapDetails) rowGap + detailsHeight else 0)
                        val detailsTop = if (wrapDetails) firstRowHeight + rowGap else 0
                        val detailsRowHeight = if (wrapDetails) detailsHeight else firstRowHeight
                        val suffixStart = levelPlaceable.width + if (wrapDetails) {
                            if (levelPlaceable.width > 0) 4.dp.roundToPx() else 0
                        } else {
                            namePlaceable.width
                        }
                        layout(width, constraints.constrainHeight(height)) {
                            namePlaceable.placeRelative(
                                if (wrapDetails) 0 else levelPlaceable.width,
                                (firstRowHeight - namePlaceable.height) / 2,
                            )
                            trailingPlaceable.placeRelative(
                                width - trailingPlaceable.width,
                                (firstRowHeight - trailingPlaceable.height) / 2,
                            )
                            levelPlaceable.placeRelative(0, detailsTop + (detailsRowHeight - levelPlaceable.height) / 2)
                            suffixPlaceable.placeRelative(
                                suffixStart,
                                detailsTop + (detailsRowHeight - suffixPlaceable.height) / 2,
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1F), verticalAlignment = Alignment.CenterVertically) {
                            levelContent()
                            nameContent(Modifier.weight(1F, fill = false))
                            nameSuffixContent()
                        }
                        nameTrailingContent()
                    }
                }
                if (descriptionContent != null) {
                    descriptionContent()
                } else {
                    Text(
                        text = sign.ifEmpty { "这个人很懒，没有签名喵" },
                        style = MaterialTheme.typography.bodyMedium,
                        minLines = signMinLines,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(0.75F),
                    )
                }
                if (showStatistics) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 2.dp)
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelMedium) {
                        Row(modifier = Modifier.fillMaxWidth(), content = statistics)
                    }
                }
            }
        }
    }
    val cardModifier = modifier
        .then(if (fixedHeight == null) Modifier.wrapContentHeight() else Modifier.height(fixedHeight))
        .fillMaxWidth()
    if (onClick == null) {
        Card(modifier = cardModifier, shape = shape, content = content)
    } else {
        Card(onClick = onClick, modifier = cardModifier, shape = shape, content = content)
    }
}
