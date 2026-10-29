package tv.hsrui.bolo.view.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.livePlayer.LivePlayer
import tv.hsrui.bolo.livePlayer.LivePlayerUiState
import tv.hsrui.bolo.livePlayer.LivePlayerViewModel
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.PlayerFullscreenEffect
import tv.hsrui.bolo.player.rememberPlayerFullscreenState
import tv.hsrui.bolo.ui.common.player.PlayerStatusBarOverlay
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.live.ShowLiveDanmakuInput
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData
import kotlin.math.roundToInt

@Composable
fun LivePlaybackScreen(
    roomId: Long,
    isActive: Boolean = true,
    viewModel: LivePlayerViewModel = composeViewModel(key = "live:$roomId") { LivePlayerViewModel(roomId) },
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val room = viewModel.roomInfo
    val infoContent = remember(viewModel) {
        movableContentOf<LiveRoomInfoData, Boolean, Boolean> { info, active, visible ->
            LivePlaybackInfo(
                room = info,
                messageManager = viewModel.messageManager,
                danmakuSender = viewModel.danmakuSender,
                isActive = active,
                isInteractionVisible = visible,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    val navigator: Navigator = koinInject()
    val settings = koinInject<BoloSettings>().live
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val session = viewModel.playbackSession
    val messagesActive = isActive && navigator.backStack.lastOrNull() == BoloRoute.View.Live(roomId) &&
        (getPlatform().type == PlatformType.Desktop || lifecycle.isAtLeast(Lifecycle.State.RESUMED))
    LaunchedEffect(viewModel, messagesActive) {
        viewModel.setMessageActive(messagesActive)
        if (!messagesActive) viewModel.danmakuSender.dismiss()
    }
    val danmakuActive = messagesActive && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(viewModel, danmakuActive) {
        viewModel.danmakuController.setActive(danmakuActive)
        onDispose { viewModel.danmakuController.setActive(false) }
    }
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.setMessageActive(false)
            viewModel.danmakuSender.dismiss()
        }
    }
    LaunchedEffect(viewModel, isActive, lifecycle, session) {
        if (isActive && lifecycle.isAtLeast(Lifecycle.State.RESUMED) &&
            navigator.backStack.lastOrNull() == BoloRoute.View.Live(roomId)) {
            viewModel.bindPlayback()
        }
    }
    var hasShownPage by remember(viewModel) { mutableStateOf(false) }
    val isPageReady = uiState is LivePlayerUiState.Success || uiState is LivePlayerUiState.Offline
    if (isPageReady) SideEffect { hasShownPage = true }
    val fullscreenState = rememberPlayerFullscreenState()
    val interactionVisible = messagesActive && lifecycle.isAtLeast(Lifecycle.State.RESUMED) &&
        !fullscreenState.isFullscreen
    if (isActive) PlayerFullscreenEffect(fullscreenState, settings.autoFullscreenOnRotateEnabled)
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isActive && fullscreenState.shouldHandleFullscreenBack,
        onBackCompleted = fullscreenState::exitFullscreen,
    )
    var rootOffset by remember { mutableStateOf(IntOffset.Zero) }
    var playerBounds by remember { mutableStateOf(IntRect.Zero) }
    val boundsTracker = Modifier.onGloballyPositioned {
        val position = it.positionInRoot()
        playerBounds = IntRect(
            IntOffset(position.x.roundToInt(), position.y.roundToInt()), it.size,
        )
    }
    Box(
        modifier.fillMaxSize().then(
            if (!fullscreenState.isDesktop && fullscreenState.isFullscreen) {
                Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            } else Modifier
        ).onGloballyPositioned {
            val position = it.positionInRoot()
            rootOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt())
        },
    ) {
        Surface(Modifier.fillMaxSize()) {
            if (hasShownPage || isPageReady) {
                Box(
                    Modifier.fillMaxSize().windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
                ) {
                    if (isExpanded()) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val infoWidth = (maxWidth * 0.3F).coerceIn(300.dp, 360.dp)
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1F).fillMaxHeight().then(boundsTracker))
                                Box(Modifier.width(infoWidth).fillMaxHeight()) {
                                    room?.let { infoContent(it, isActive, interactionVisible) }
                                }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16F / 9F).then(boundsTracker))
                            Box(Modifier.weight(1F).fillMaxWidth()) {
                                room?.let { infoContent(it, isActive, interactionVisible) }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ShowTopBarWithNavigationButton(
                        onBack = if (fullscreenState.isDesktop) fullscreenState::goBack else null,
                        title = {},
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (val state = uiState) {
                            LivePlayerUiState.Loading -> CircularProgressIndicator()
                            is LivePlayerUiState.Error -> ShowErrorContent(
                                message = state.message, retry = viewModel::loadPlayback,
                            )
                            is LivePlayerUiState.Offline, is LivePlayerUiState.Success -> Unit
                        }
                    }
                }
            }
        }
        if ((hasShownPage || isPageReady) &&
            (fullscreenState.isFullscreen || !playerBounds.isEmpty)) {
            val bounds = playerBounds.translate(-rootOffset.x, -rootOffset.y)
            val playerModifier = if (fullscreenState.isFullscreen) Modifier.fillMaxSize() else {
                Modifier.offset { bounds.topLeft }.layout { measurable, _ ->
                    val width = bounds.width.coerceAtLeast(1)
                    val height = bounds.height.coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
            }
            // 播放宿主始终位于同一组合位置，尺寸和全屏切换只改变布局边界。
            LivePlayer(viewModel, fullscreenState, playerModifier.background(Color.Black).zIndex(1f))
        }
        PlayerStatusBarOverlay(isFullscreen = fullscreenState.isFullscreen)
        if (messagesActive && viewModel.danmakuSender.isOpen) {
            ShowLiveDanmakuInput(
                sender = viewModel.danmakuSender,
                fansMedal = viewModel.messageManager.fansMedal,
            )
        }
    }
}
