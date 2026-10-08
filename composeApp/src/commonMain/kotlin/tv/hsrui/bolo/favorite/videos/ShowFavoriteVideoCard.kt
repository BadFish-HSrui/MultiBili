package tv.hsrui.bolo.favorite.videos

import tv.hsrui.bolo.navigation.openUserSpace
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.hsrui.bolo.navigation.openMedia
import tv.hsrui.bolo.navigation.openVideo
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.network.feature.favorite.FavoriteVideoCard
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.formatToDuration

@Composable
fun ShowFavoriteVideoCard(
    videoInfo: FavoriteVideoCard,
    onRemove: suspend () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = false,
) {
    val canOpenVideo = videoInfo.isAvailable && when {
        videoInfo.isMedia -> videoInfo.episodeId > 0
        videoInfo.isVideo -> videoInfo.bvid.isNotEmpty() || videoInfo.resourceId > 0
        else -> false
    }

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
                    if (videoInfo.isMedia) {
                        openMedia(episodeId = videoInfo.episodeId)
                    } else if (videoInfo.bvid.isNotEmpty()) {
                        openVideo(bvid = videoInfo.bvid)
                    } else {
                        openVideo(avid = videoInfo.resourceId)
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.55F),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Text(
                        text = videoInfo.duration.formatToDuration(),
                        color = Color.White,
                        style = TextStyle(fontSize = 10.sp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1F)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                Text(
                    text = videoInfo.title,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(end = if (canManage) 28.dp else 0.dp)
                        .alpha(0.75F)
                ) {
                    if (!videoInfo.isMedia) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(32.dp)
                                .clickable(enabled = videoInfo.upMid > 0) { openUserSpace(videoInfo.upMid) }
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
                    }
                    Column(
                        modifier = Modifier
                            .weight(1F)
                            .padding(start = if (videoInfo.isMedia) 0.dp else 4.dp)
                    ) {
                        if (!videoInfo.isMedia) {
                            Text(
                                text = videoInfo.upName.ifEmpty { "未知UP主" },
                                modifier = Modifier.clickable(enabled = videoInfo.upMid > 0) { openUserSpace(videoInfo.upMid) },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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

                if (canManage) {
                    var showDialog by rememberSaveable { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    IconButton(
                        onClick = { showDialog = true },
                        enabled = videoInfo.resourceId > 0,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RemoveCircleOutline,
                            contentDescription = "取消收藏",
                            modifier = Modifier.size(20.dp).alpha(0.5F)
                        )
                    }
                    if (showDialog) {
                        ShowConfirmDialog(
                            title = { Text("取消收藏") },
                            onCancel = { showDialog = false },
                            onConfirm = {
                                scope.launch {
                                    onRemove()
                                    showDialog = false
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = null
                                )
                            },
                            text = buildString {
                                appendLine("确认从当前收藏夹移除:")
                                appendLine(videoInfo.title)
                                appendLine()
                                append("*会同时在所有设备上取消收藏*")
                            }
                        )
                    }
                }
            }
        }
    }
}
