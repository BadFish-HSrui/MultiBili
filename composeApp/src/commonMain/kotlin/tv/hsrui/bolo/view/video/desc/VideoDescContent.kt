package tv.hsrui.bolo.view.video.desc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.ui.components.button.RelationButton
import tv.hsrui.bolo.ui.components.video.actionsBar.VideoActionsBar
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.utils.formatCountToString
import tv.hsrui.network.utils.formatToDateTime
import kotlin.text.ifEmpty

@Composable
fun VideoDescContent(videoInfo: VideoInfoData) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = videoInfo.upAvatarUrl + "@96w_96h_1c.webp",
                    contentDescription = "UP头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(32.dp)
                        .fillMaxWidth()
                        .aspectRatio(1F)
                        .clip(CircleShape)
                        .background(Color.Black)
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        text = videoInfo.upName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.weight(1F))
                RelationButton(
                    upName = videoInfo.upName,
                    mid = videoInfo.upMid,
                    modifier = Modifier.height(24.dp)
                )
            }

            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.labelSmall,
            ) {
                Column(modifier = Modifier.alpha(0.5F).padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = "播放量",
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            videoInfo.stateCount.view.formatCountToString()
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ShortText,
                            contentDescription = "弹幕量",
                            modifier = Modifier.padding(start = 8.dp).size(12.dp)
                        )
                        Text(
                            videoInfo.stateCount.danmaku.formatCountToString()
                        )

                        Icon(
                            imageVector = Icons.Rounded.Publish,
                            contentDescription = "发布时间",
                            modifier = Modifier.padding(start = 12.dp).size(12.dp)
                        )
                        Text(
                            videoInfo.publishDate.formatToDateTime(second = true),
                        )
                    }
                    if (videoInfo.argueMessage.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = "警告信息",
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                videoInfo.argueMessage,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            var isDescExpand by remember { mutableStateOf(false) }

            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = videoInfo.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (isDescExpand) 5 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1F)
                )
                IconButton(
                    onClick = { isDescExpand = !isDescExpand },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (isDescExpand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "展开简介"
                    )
                }
            }
            if (isDescExpand) {
                Text(
                    text = videoInfo.description.ifEmpty { "——" },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 4.dp).alpha(0.67F)
                )
                if (videoInfo.dynamicDescription.isNotEmpty()) {
                    OutlinedCard(Modifier.padding(4.dp).fillMaxWidth()) {
                        Text(
                            text = "动态简介:",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                        Text(
                            text = videoInfo.dynamicDescription,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 8.dp)
                                .alpha(0.67F)
                        )
                    }
                }
            }
            VideoActionsBar(videoInfo = videoInfo, modifier = Modifier.padding(top = 8.dp))
        }
    }
}