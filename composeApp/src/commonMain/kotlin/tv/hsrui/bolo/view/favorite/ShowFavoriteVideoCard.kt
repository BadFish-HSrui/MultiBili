package tv.hsrui.bolo.view.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.navigation.openVideo
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.network.feature.favorite.FavoriteVideoCard
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.formatToDuration

@Composable
fun ShowFavoriteVideoCard(
    videoInfo: FavoriteVideoCard,
    modifier: Modifier = Modifier
) {
    val canOpenVideo = videoInfo.isAvailable && (videoInfo.bvid.isNotEmpty() || videoInfo.avid > 0)

    Card(
        shape = BoloShapes.InfoCard.Default,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 512.dp)
            .height(88.dp)
            .alpha(if (videoInfo.isAvailable) 1F else 0.55F)
            .combinedClickable(
                enabled = canOpenVideo,
                onClick = {
                    if (videoInfo.bvid.isNotEmpty()) {
                        openVideo(bvid = videoInfo.bvid)
                    } else {
                        openVideo(avid = videoInfo.avid)
                    }
                }
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16F / 9F)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = videoInfo.coverUrl
                        .takeIf { it.isNotEmpty() }
                        ?.plus("@320w_180h_1c.webp"),
                    contentDescription = "视频封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (videoInfo.duration > 0) {
                    Text(
                        text = videoInfo.duration.formatToDuration(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.55F),
                                shape = BoloShapes.InfoCard.Compact
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1F)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                Text(
                    text = videoInfo.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = videoInfo.upAvatarUrl
                                .takeIf { it.isNotEmpty() }
                                ?.plus("@64w_64h.webp"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = 4.dp)
                    ) {
                        Text(
                            text = videoInfo.upName.ifEmpty { "未知UP主" },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (videoInfo.favoriteTime > 0) {
                                "收藏于 ${videoInfo.favoriteTime.formatToDateTime()}"
                            } else {
                                "收藏时间未知"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
