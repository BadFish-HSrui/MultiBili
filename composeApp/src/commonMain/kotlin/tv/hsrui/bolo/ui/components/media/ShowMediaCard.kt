package tv.hsrui.bolo.ui.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.bolo.utils.isCompact
import tv.hsrui.network.model.MediaCard

@Composable
fun ShowMediaCard(
    mediaInfo: MediaCard,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val compact = isCompact()
    val shape = if (compact) BoloShapes.InfoCard.Compact else BoloShapes.InfoCard.Default
    val infoTextSize = if (compact) 10.sp else 12.sp
    val isVipBadge = mediaInfo.badge.contains("会员")
    val isExclusiveBadge = mediaInfo.badge == "独家"
    val isPreviewBadge = mediaInfo.badge.contains("预告")
    val badgeColor = when {
        isVipBadge -> BiliColor.ThemeColor
        isExclusiveBadge || isPreviewBadge -> BiliColor.Blue
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContentColor = if (isVipBadge || isExclusiveBadge || isPreviewBadge) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

    val coverContent: @Composable BoxScope.() -> Unit = {
        if (mediaInfo.coverUrl.isNotBlank()) {
            AsyncImage(
                model = mediaInfo.coverUrl + if (horizontal) "@320w_180h_1c.webp" else "@600w_900h_1c.webp",
                contentDescription = "${mediaInfo.title}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (mediaInfo.badge.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = badgeColor,
                contentColor = badgeContentColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Text(
                    text = mediaInfo.badge,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (mediaInfo.scoreText.isNotBlank() || mediaInfo.progressText.isNotBlank()) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    ),
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (mediaInfo.scoreText.isNotBlank()) {
                    Text(
                        text = mediaInfo.scoreText,
                        color = Color.White,
                        fontSize = infoTextSize,
                        maxLines = 1,
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (mediaInfo.progressText.isNotBlank()) {
                        Text(
                            text = mediaInfo.progressText,
                            color = Color.White,
                            fontSize = infoTextSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
    }

    val content: @Composable ColumnScope.() -> Unit = {
        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small),
                    content = coverContent,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        mediaInfo.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        mediaInfo.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f), content = coverContent)
            Text(
                text = mediaInfo.title,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp).height(40.dp),
            )
            if (mediaInfo.subtitle.isNotBlank()) {
                Text(
                    text = mediaInfo.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
    if (onClick == null) Card(shape = shape, modifier = modifier, content = content)
    else Card(onClick = onClick, shape = shape, modifier = modifier, content = content)
}
