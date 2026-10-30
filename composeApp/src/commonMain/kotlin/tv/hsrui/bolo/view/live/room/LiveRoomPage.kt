package tv.hsrui.bolo.view.live.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.button.RelationButton
import tv.hsrui.bolo.utils.isMedium
import tv.hsrui.network.feature.user.info.UserCardStatsData
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData
import tv.hsrui.network.utils.formatCountToString
import tv.hsrui.network.utils.formatToDateTime

@Composable
fun LiveRoomPage(
    room: LiveRoomInfoData,
    onUpClick: (Long) -> Unit,
    isActive: Boolean = true,
    viewModel: LiveRoomViewModel = viewModel(key = "live_room_${room.roomId}_up_${room.upMid}") {
        LiveRoomViewModel(room.upMid)
    },
    modifier: Modifier = Modifier,
) {
    val stats by viewModel.stats.collectAsState()
    val navigator: Navigator = koinInject()
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (isMedium()) modifier.fillMaxWidth() else modifier.widthIn(max = 512.dp),
    ) {
        item(key = "room_${room.roomId}") {
            LiveRoomContent(
                room = room,
                upStats = stats,
                onUpClick = onUpClick,
                onAreaClick = { parentId, areaId ->
                    if (isActive) navigator.navigateTo(BoloRoute.Live.Area(parentId, areaId))
                },
            )
        }
        if (room.announcement.isNotBlank()) {
            item(key = "announcement_${room.roomId}") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text("直播公告", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = room.announcement,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp).alpha(0.67f),
                        )
                    }
                }
            }
        }
        if (room.description.isNotBlank()) {
            item(key = "description_${room.roomId}") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text("直播简介", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = room.description,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp).alpha(0.67f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRoomContent(
    room: LiveRoomInfoData,
    upStats: UserCardStatsData?,
    onUpClick: (Long) -> Unit,
    onAreaClick: (Int, Int?) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = room.upAvatarUrl.takeIf { it.isNotBlank() }?.plus("@96w_96h_1c.webp"),
                    contentDescription = "UP头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .clickable(enabled = room.upMid > 0) { onUpClick(room.upMid) }
                        .background(Color.Black),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = room.upName,
                        modifier = Modifier.clickable(enabled = room.upMid > 0) { onUpClick(room.upMid) },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${(upStats?.followerCount ?: room.followerCount)?.formatCountToString() ?: "--"} 粉丝   " +
                            "${upStats?.uploadCount?.formatCountToString() ?: "--"} 投稿",
                        modifier = Modifier.alpha(0.5f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                key(room.upMid) {
                    RelationButton(upName = room.upName, mid = room.upMid, modifier = Modifier.height(24.dp))
                }
            }
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelSmall) {
                FlowRow(
                    modifier = Modifier.alpha(0.5f).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Visibility, "看过人数", Modifier.size(12.dp))
                        Text(room.watchedCount?.formatCountToString() ?: "--")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Whatshot, "人气", Modifier.size(12.dp))
                        Text(room.popularity?.formatCountToString() ?: "--")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Publish, "开播时间", Modifier.size(12.dp))
                        Text(room.liveStartTimeEpochSeconds?.formatToDateTime(second = true) ?: "--")
                    }
                }
            }
            Text(
                text = room.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            val areaName = listOf(room.parentAreaName, room.areaName)
                .filter { it.isNotBlank() }.joinToString(" · ")
            val parentAreaId = room.parentAreaId
            val areaId = room.areaId
            if (areaName.isNotEmpty()) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
                    Surface(
                        onClick = {
                            if (parentAreaId != null && areaId != null) onAreaClick(parentAreaId, areaId)
                        },
                        enabled = parentAreaId != null && areaId != null,
                        modifier = Modifier.padding(top = 8.dp).heightIn(min = 24.dp),
                        shape = CircleShape,
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = areaName,
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
