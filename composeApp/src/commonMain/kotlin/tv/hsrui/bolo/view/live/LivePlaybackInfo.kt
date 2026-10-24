package tv.hsrui.bolo.view.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.userSpace.ShowUserUploadsSheet
import tv.hsrui.bolo.view.live.room.LiveRoomPage
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData

@Composable
fun LivePlaybackInfo(
    room: LiveRoomInfoData,
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    key(room.roomId) {
        val settings: BoloSettings = koinInject()
        val tabs = listOf("直播间", "弹幕")
        val pagerState = rememberPagerState { tabs.size }
        val coroutineScope = rememberCoroutineScope()
        var selectedUpMid by remember(room.upMid) { mutableStateOf<Long?>(null) }
        LaunchedEffect(isActive, settings.general.upRecentUploadsSheetEnabled) {
            if (!isActive || !settings.general.upRecentUploadsSheetEnabled) selectedUpMid = null
        }
        Column(modifier) {
            PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> LiveRoomPage(
                        room = room,
                        onUpClick = { mid ->
                            if (isActive && mid > 0) {
                                if (settings.general.upRecentUploadsSheetEnabled) selectedUpMid = mid
                                else openUserSpace(mid)
                            }
                        },
                        isActive = isActive,
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> Box(Modifier.fillMaxSize())
                }
            }
        }
        if (isActive && settings.general.upRecentUploadsSheetEnabled) {
            selectedUpMid?.let { mid ->
                ShowUserUploadsSheet(mid = mid, onDismissRequest = { selectedUpMid = null })
            }
        }
    }
}
