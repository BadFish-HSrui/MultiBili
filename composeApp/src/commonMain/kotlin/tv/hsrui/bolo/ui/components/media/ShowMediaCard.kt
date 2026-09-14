package tv.hsrui.bolo.ui.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
fun ShowMediaCard(mediaInfo: MediaCard, modifier: Modifier = Modifier) {
    val compact = isCompact()
    val shape = if (compact) BoloShapes.InfoCard.Compact else BoloShapes.InfoCard.Default
    val infoTextSize = if (compact) 10.sp else 12.sp

    Card(shape = shape, modifier = modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            if (mediaInfo.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = mediaInfo.coverUrl + "@600w_900h_1c.webp",
                    contentDescription = "${mediaInfo.title}封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (mediaInfo.badge.isNotBlank()) {
                val isVipBadge = mediaInfo.badge.contains("会员")
                val isExclusiveBadge = mediaInfo.badge == "独家"
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = when {
                        isVipBadge -> BiliColor.ThemeColor
                        isExclusiveBadge -> BiliColor.Blue
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (isVipBadge || isExclusiveBadge) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Text(
                        text = mediaInfo.badge,
                        fontSize = infoTextSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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
