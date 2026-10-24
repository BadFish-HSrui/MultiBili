package tv.hsrui.bolo.ui.components.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.navigation.openLive
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.bolo.utils.isCompact
import tv.hsrui.network.model.LiveCard
import tv.hsrui.network.utils.formatCountToString
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.formatToDurationText

@Composable
fun ShowLiveCard(
    liveInfo: LiveCard,
    modifier: Modifier = Modifier,
    showStatistics: Boolean = true,
    showOfflineStatus: Boolean = false,
    currentTime: Instant = Clock.System.now(),
) {
    val navigator: Navigator = koinInject()
    val compact = isCompact()
    val coverSuffix = if (compact) "@400w_225h_1c.webp" else "@800w_450h_1c.webp"

    Card(
        onClick = { openLive(liveInfo.roomId) },
        enabled = liveInfo.roomId > 0,
        shape = if (compact) BoloShapes.InfoCard.Compact else BoloShapes.InfoCard.Default,
        modifier = modifier,
    ) {
        Column {
            Box(modifier = Modifier.aspectRatio(16f / 9f)) {
                AsyncImage(
                    model = liveInfo.coverUrl.takeIf { it.isNotBlank() }?.plus(coverSuffix),
                    contentDescription = "直播封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (showStatistics) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(
                                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))
                            ),
                    )
                    CompositionLocalProvider(
                        LocalContentColor provides Color.White,
                        LocalTextStyle provides TextStyle(fontSize = if (compact) 10.sp else 12.sp),
                    ) {
                        Row(Modifier.align(Alignment.TopStart)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Visibility,
                                    contentDescription = "看过人数",
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(liveInfo.watchedCount.formatCountToString())
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Whatshot,
                                    contentDescription = "人气",
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(liveInfo.popularity.formatCountToString())
                            }
                        }
                    }
                }
                if (showOfflineStatus && liveInfo.liveStatus != 1) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatOfflineText(liveInfo.recordLiveTime, currentTime),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            Text(
                text = liveInfo.title,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp).height(40.dp),
            )
            HorizontalDivider(Modifier.height(2.dp))
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp).alpha(0.5f)) {
                Row(
                    modifier = Modifier.clickable(enabled = liveInfo.upMid > 0) { openUserSpace(liveInfo.upMid) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountBox,
                        contentDescription = "主播",
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = liveInfo.upName,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = liveInfo.areaDisplayName,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = liveInfo.parentAreaId > 0) {
                        navigator.navigateTo(
                            BoloRoute.Live.Area(liveInfo.parentAreaId, liveInfo.areaId.takeIf { it > 0 })
                        )
                    },
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatOfflineText(recordLiveTime: Long, currentTime: Instant): String {
    if (recordLiveTime <= 0) return "未开播"
    return try {
        val endTime = Instant.fromEpochSeconds(recordLiveTime)
        val elapsed = currentTime - endTime
        when {
            elapsed < 1.minutes -> "刚刚下播"
            elapsed < 14.days -> "${elapsed.inWholeSeconds.formatToDurationText()}前下播"
            else -> {
                val zone = TimeZone.currentSystemDefault()
                recordLiveTime.formatToDateTime(
                    year = endTime.toLocalDateTime(zone).year != currentTime.toLocalDateTime(zone).year,
                    hourMinute = false,
                ) + "下播"
            }
        }
    } catch (_: IllegalArgumentException) {
        "未开播"
    }
}