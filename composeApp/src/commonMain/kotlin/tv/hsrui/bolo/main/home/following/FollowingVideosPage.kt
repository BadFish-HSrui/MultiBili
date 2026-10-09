package tv.hsrui.bolo.main.home.following

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.utils.isExpanded

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FollowingVideosPage(
    modifier: Modifier = Modifier,
    followingVideosViewModel: FollowingVideosViewModel = viewModel { FollowingVideosViewModel() },
    reselectEvents: Flow<Unit>? = null,
    isSelected: Boolean = true,
) {
    val followingVideosUiState by followingVideosViewModel.uiState.collectAsState()
    val hasNewVideos by followingVideosViewModel.hasNewVideos.collectAsState()
    val gridState = rememberLazyGridState()
    val navigator: Navigator = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive = isSelected && navigator.backStack.lastOrNull() == BoloRoute.Main

    LaunchedEffect(followingVideosViewModel, lifecycleOwner, isActive) {
        if (isActive) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                followingVideosViewModel.runUpdateChecks()
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        VideosGridPage(
            uiState = followingVideosUiState,
            isLoading = followingVideosViewModel.isLoading,
            onRefresh = followingVideosViewModel::refreshVideos,
            onLoadMore = followingVideosViewModel::loadMoreVideos,
            reselectEvents = reselectEvents,
            videoGridState = gridState,
            isRefreshing = followingVideosViewModel.isRefreshing,
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = hasNewVideos && isActive && !followingVideosViewModel.isRefreshing,
            enter = slideInVertically(animationSpec = tween(500)) { -it } +
                fadeIn(animationSpec = tween(500)),
            exit = slideOutVertically(animationSpec = tween(300)) { -it } +
                fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopCenter).padding(
                start = 12.dp,
                top = if (isExpanded()) 12.dp else 8.dp,
                end = 12.dp,
            ),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                OutlinedCard(
                    onClick = {
                        gridState.requestScrollToItem(0)
                        followingVideosViewModel.refreshVideos()
                    },
                    enabled = !followingVideosViewModel.isRefreshing,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "有新视频，点击刷新",
                        style = MaterialTheme.typography.labelMedium,
                        color = BiliColor.Blue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
