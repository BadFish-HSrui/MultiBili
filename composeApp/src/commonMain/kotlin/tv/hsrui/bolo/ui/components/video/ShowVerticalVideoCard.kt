package tv.hsrui.bolo.ui.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.navigation.openVideo
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideoCardExample
import tv.hsrui.network.utils.formatCountToString

@Composable
fun ShowVerticalVideoCard(
    videoInfo: VideoCard,
    modifier: Modifier = Modifier,
    dropdownMenuItems: @Composable (ColumnScope.(onDismiss: () -> Unit) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .widthIn(max = 512.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = { openVideo(videoInfo.bvid) }
            )
    ) {
        Row {
            Box(modifier = Modifier.aspectRatio(16F / 9F).background(Color.White)) {
                AsyncImage(
                    model = videoInfo.coverUrl + "@480w_270h_1c.webp",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Text(
                    text = videoInfo.durationString,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 4.dp),
                )

            }
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = videoInfo.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 4.dp, end = 4.dp)
                )

                Column(Modifier.alpha(0.75F).align(Alignment.BottomStart)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBox,
                            contentDescription = "UP主",
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = videoInfo.upName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Row {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircleOutline,
                                contentDescription = "播放量",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                videoInfo.viewCount.formatCountToString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ShortText,
                                contentDescription = "弹幕量",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                videoInfo.danmakuCount.formatCountToString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (dropdownMenuItems != null){
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { expanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "更多操作",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            shape = BoloShapes.InfoCard.Default
                        ) {
                            dropdownMenuItems { expanded = false }
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 80)
@Composable
private fun PreviewVerticalVideoCard() {
    ShowVerticalVideoCard(VideoCardExample)
}