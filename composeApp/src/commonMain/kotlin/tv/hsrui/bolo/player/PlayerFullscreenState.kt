package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform

@Stable
class PlayerFullscreenState internal constructor(initialFullscreen: Boolean = false) {
    private val platform = getPlatform().type
    val isDesktop = platform == PlatformType.Desktop
    private val usesIosFullscreen = platform == PlatformType.Ios

    internal var iosFullscreenTarget by mutableStateOf(initialFullscreen)
        private set
    internal var iosFullscreenGeneration by mutableStateOf(0L)
        private set
    internal var isChangingIosFullscreen by mutableStateOf(usesIosFullscreen && initialFullscreen)
        private set
    internal var onIosFullscreenRequest: (() -> Unit)? = null

    var isFullscreen by mutableStateOf(initialFullscreen && !usesIosFullscreen)
        private set
    var isSystemFullscreen by mutableStateOf(false)
        private set
    internal var isSystemFullscreenOwnedByPlayer by mutableStateOf(false)
        private set

    internal var systemFullscreenRequest by mutableStateOf<Boolean?>(null)
        private set
    private var fullscreenBeforeSystem: Boolean? = null
    internal var dispatchBack: (() -> Unit)? = null
    internal var releaseSystemFullscreenOwnership: (() -> Unit)? = null

    val isChangingSystemFullscreen: Boolean get() = systemFullscreenRequest != null
    val canExitFullscreen: Boolean
        get() = isFullscreen || isSystemFullscreen || isChangingSystemFullscreen || isChangingIosFullscreen

    fun toggleWindowFullscreen() {
        if (usesIosFullscreen) {
            requestIosFullscreen(!iosFullscreenTarget)
            return
        }
        isFullscreen = !isFullscreen
        if (isDesktop && !isFullscreen && isSystemFullscreenOwnedByPlayer) {
            isSystemFullscreenOwnedByPlayer = false
            releaseSystemFullscreenOwnership?.invoke()
        }
    }

    fun toggleFullscreen() {
        if (isDesktop) requestSystemFullscreen(!isSystemFullscreen) else toggleWindowFullscreen()
    }

    fun goBack() {
        dispatchBack?.invoke()
    }

    fun exitFullscreen() {
        when {
            usesIosFullscreen -> requestIosFullscreen(false)
            isChangingSystemFullscreen -> Unit
            isSystemFullscreen -> requestSystemFullscreen(false)
            else -> isFullscreen = false
        }
    }

    private fun requestIosFullscreen(fullscreen: Boolean) {
        if (iosFullscreenTarget == fullscreen) return
        iosFullscreenTarget = fullscreen
        iosFullscreenGeneration++
        isChangingIosFullscreen = true
        onIosFullscreenRequest?.invoke()
    }

    // 仅由 iOS 宿主在原生几何与 Compose 视口一致时提交展示状态。
    internal fun updateIosFullscreenLayout(fullscreen: Boolean) {
        if (usesIosFullscreen) isFullscreen = fullscreen
    }

    internal fun completeIosFullscreenRequest(generation: Long, fullscreen: Boolean) {
        if (!usesIosFullscreen || generation != iosFullscreenGeneration) return
        isFullscreen = fullscreen
        iosFullscreenTarget = fullscreen
        isChangingIosFullscreen = false
    }

    private fun requestSystemFullscreen(fullscreen: Boolean) {
        if (isChangingSystemFullscreen) return
        if (fullscreen) {
            fullscreenBeforeSystem = isFullscreen
            isFullscreen = true
            // 请求尚未交给宿主时缩小播放器，也应放弃本次系统全屏的归属。
            isSystemFullscreenOwnedByPlayer = true
        }
        systemFullscreenRequest = fullscreen
    }

    internal fun updateSystemFullscreen(fullscreen: Boolean, ownedByPlayer: Boolean) {
        isSystemFullscreenOwnedByPlayer = fullscreen && ownedByPlayer
        if (isSystemFullscreen == fullscreen) return
        if (fullscreen) {
            // 系统按钮进入时只记录布局；播放器发起的请求已经记录过。
            if (fullscreenBeforeSystem == null) fullscreenBeforeSystem = isFullscreen
        } else {
            restorePlayerLayout()
        }
        isSystemFullscreen = fullscreen
    }

    internal fun completeSystemFullscreenRequest(fullscreen: Boolean, ownedByPlayer: Boolean) {
        updateSystemFullscreen(fullscreen, ownedByPlayer)
        if (systemFullscreenRequest == true && !fullscreen) restorePlayerLayout()
        systemFullscreenRequest = null
    }

    private fun restorePlayerLayout() {
        fullscreenBeforeSystem?.let { isFullscreen = it }
        fullscreenBeforeSystem = null
    }

    internal companion object {
        val Saver = Saver<PlayerFullscreenState, Boolean>(
            save = { if (it.usesIosFullscreen) it.iosFullscreenTarget else it.fullscreenBeforeSystem ?: it.isFullscreen },
            restore = { PlayerFullscreenState(it) },
        )
    }
}

@Composable
fun rememberPlayerFullscreenState(initialFullscreen: Boolean = false): PlayerFullscreenState {
    val state = rememberSaveable(saver = PlayerFullscreenState.Saver) { PlayerFullscreenState(initialFullscreen) }
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    DisposableEffect(state, dispatcher) {
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        state.dispatchBack = input::backCompleted
        onDispose {
            state.dispatchBack = null
            dispatcher.removeInput(input)
        }
    }
    return state
}
