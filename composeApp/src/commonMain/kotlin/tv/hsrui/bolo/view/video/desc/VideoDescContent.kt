package tv.hsrui.bolo.view.video.desc

import tv.hsrui.bolo.navigation.openUserSpace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
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
fun VideoDescContent(
    videoInfo: VideoInfoData,
    tags: List<String>,
    onTagClick: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(8.dp)) {
            if (videoInfo.isCooperation && videoInfo.staff.isNotEmpty()) {
                key(videoInfo.bvid, videoInfo.avid) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        videoInfo.staff.forEach { member ->
                            key(member.mid) {
                                Column(
                                    modifier = Modifier.width(72.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AsyncImage(
                                        model = member.avatarUrl + "@96w_96h_1c.webp",
                                        contentDescription = "UP头像",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(48.dp)
                                            .clip(CircleShape)
                                            .clickable { openUserSpace(member.mid) }
                                            .background(Color.Black),
                                    )
                                    RelationButton(
                                        upName = member.name,
                                        mid = member.mid,
                                        modifier = Modifier.height(24.dp),
                                    )
                                    Text(
                                        text = member.name,
                                        modifier = Modifier.fillMaxWidth().clickable { openUserSpace(member.mid) },
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = member.role,
                                        modifier = Modifier.fillMaxWidth().alpha(0.7F),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        minLines = 1,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = videoInfo.upAvatarUrl + "@96w_96h_1c.webp",
                        contentDescription = "UP头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .fillMaxWidth()
                            .aspectRatio(1F)
                            .clip(CircleShape)
                            .clickable(enabled = videoInfo.upMid > 0) { openUserSpace(videoInfo.upMid) }
                            .background(Color.Black)
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            text = videoInfo.upName,
                            modifier = Modifier.clickable(enabled = videoInfo.upMid > 0) { openUserSpace(videoInfo.upMid) },
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

            var isDescExpand by rememberSaveable { mutableStateOf(false) }

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
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp).alpha(0.67F)
                )
                if (videoInfo.dynamicDescription.isNotEmpty()) {
                    OutlinedCard(Modifier.padding(4.dp).fillMaxWidth()) {
                        Text(
                            text = "动态简介:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                        Text(
                            text = videoInfo.dynamicDescription,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp)
                                .alpha(0.67F)
                        )
                    }
                }
                if (tags.isNotEmpty()) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    onClick = { onTagClick(tag) },
                                    modifier = Modifier.height(24.dp),
                                    shape = CircleShape,
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            VideoActionsBar(videoInfo = videoInfo, modifier = Modifier.padding(top = 8.dp))
        }
    }
}