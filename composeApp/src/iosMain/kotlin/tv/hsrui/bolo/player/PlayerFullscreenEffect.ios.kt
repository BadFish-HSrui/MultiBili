@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations
import platform.darwin.NSObjectProtocol
import kotlin.math.abs
import kotlin.native.ref.WeakReference

internal val LocalIosPlayerFullscreenCoordinator = staticCompositionLocalOf<IosPlayerFullscreenCoordinator> {
    error("Missing iOS fullscreen coordinator")
}

@Composable
actual fun PlayerFullscreenEffect(fullscreenState: PlayerFullscreenState) {
    val coordinator = LocalIosPlayerFullscreenCoordinator.current
    val viewport = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    DisposableEffect(coordinator, fullscreenState) {
        coordinator.bind(fullscreenState)
        onDispose { coordinator.unbind(fullscreenState) }
    }
    SideEffect { coordinator.updateViewport(fullscreenState, viewport, density) }
}

// 一个宿主串行协调窗口请求；页面只提交目标，原生几何和 Compose 视口共同确认展示状态。
internal class IosPlayerFullscreenCoordinator {
    private var hostReference: WeakReference<UIViewController>? = null
    var host: UIViewController?
        get() = hostReference?.get()
        set(value) { hostReference = value?.let(::WeakReference) }
    private class Viewport(val size: IntSize, val density: Float)
    private class Request(
        val owner: PlayerFullscreenState?,
        val generation: Long,
        val target: Boolean,
        val submitted: Boolean,
        var checkedAt: Double,
    ) {
        var remainingSeconds = 3.0
        var wasForeground = true
        var error: String? = null
    }

    private val owners = mutableListOf<PlayerFullscreenState>()
    private val viewports = mutableMapOf<PlayerFullscreenState, Viewport>()
    private val owner get() = owners.lastOrNull()
    private var scope: CoroutineScope? = null
    private var timer: Job? = null
    private val observers = mutableListOf<NSObjectProtocol>()
    private var request: Request? = null
    private var transitions = 0
    private var restorePortrait = false
    private var needsReconcile = false
    private var reconciling = false
    private var sceneIsActive: Boolean? = null

    fun bind(state: PlayerFullscreenState) {
        if (state in owners) return
        owners.add(state)
        restorePortrait = false
        needsReconcile = true
        state.onIosFullscreenRequest = {
            if (owner === state) {
                needsReconcile = true
                reconcile()
            }
        }
        if (scope == null) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            for (name in listOf(UISceneDidActivateNotification, UISceneWillDeactivateNotification)) {
                observers += NSNotificationCenter.defaultCenter.addObserverForName(
                    name, null, NSOperationQueue.mainQueue,
                ) { notification ->
                    if (notification?.`object` == host?.view?.window?.windowScene) {
                        sceneIsActive = name == UISceneDidActivateNotification
                        if (sceneIsActive == true) {
                            needsReconcile = true
                            // 后台期间目标可能改变；回前台不继续追逐已被替换的请求。
                            request?.let { pending ->
                                if (transitions == 0 &&
                                    (pending.owner !== owner || pending.target != (owner?.iosFullscreenTarget ?: false))
                                ) clearRequest()
                            }
                        }
                        reconcile()
                    }
                }
            }
        }
        reconcile()
    }

    fun unbind(state: PlayerFullscreenState) {
        if (!owners.remove(state)) return
        state.onIosFullscreenRequest = null
        viewports.remove(state)
        restorePortrait = owners.isEmpty()
        needsReconcile = true
        reconcile()
    }

    fun updateViewport(state: PlayerFullscreenState, size: IntSize, density: Float) {
        if (state !in owners) return
        val previous = viewports[state]
        if (previous?.size == size && previous.density == density) return
        viewports[state] = Viewport(size, density)
        if (owner === state) {
            reconcile()
        }
    }

    fun geometryChanged() = reconcile()

    fun transitionStarted() {
        transitions++
    }

    fun transitionFinished() {
        transitions = (transitions - 1).coerceAtLeast(0)
        needsReconcile = owner != null || restorePortrait
        reconcile()
    }

    private fun isForeground(): Boolean =
        sceneIsActive != false &&
            host?.view?.window?.windowScene?.activationState == UISceneActivationStateForegroundActive

    private fun observedLayout(state: PlayerFullscreenState?): Boolean? {
        val controller = host ?: return null
        val scene = controller.view.window?.windowScene ?: return null
        val landscape = when (scene.interfaceOrientation) {
            UIInterfaceOrientationLandscapeLeft, UIInterfaceOrientationLandscapeRight -> true
            UIInterfaceOrientationPortrait, UIInterfaceOrientationPortraitUpsideDown -> false
            else -> return null
        }
        val nativeSize = controller.view.bounds.useContents { size.width to size.height }
        if (nativeSize.first <= 0 || nativeSize.second <= 0) return null
        if ((nativeSize.first > nativeSize.second) != landscape) return null
        if (state != null) {
            val viewport = viewports[state] ?: return null
            if (viewport.size.width <= 0 || viewport.size.height <= 0) return null
            if (abs(nativeSize.first * viewport.density - viewport.size.width) > 1.0 ||
                abs(nativeSize.second * viewport.density - viewport.size.height) > 1.0
            ) return null
        }
        return landscape
    }

    private fun reconcile() {
        if (scope == null || reconciling) return
        reconciling = true
        try {
            if (host == null || (owner == null && host?.view?.window == null)) {
                clearRequest()
                restorePortrait = false
                stopObserving()
                return
            }
            val foreground = isForeground()
            val now = CACurrentMediaTime()
            request?.let {
                if (it.wasForeground) it.remainingSeconds -= now - it.checkedAt
                it.checkedAt = now
                it.wasForeground = foreground
            }
            if (!foreground) {
                timer?.cancel()
                timer = null
                return
            }

            request?.let { waiting ->
                if (!waiting.submitted && (waiting.owner !== owner || waiting.target != owner?.iosFullscreenTarget)) {
                    clearRequest()
                }
            }
            val active = request
            if (active != null) {
                val observed = observedLayout(owner)
                if (observed == active.target) {
                    // 即便目标已反向，实际到达的几何仍须用于当前帧；旧请求不得改写最新目标。
                    owner?.let { state ->
                        if (state === active.owner && state.isFullscreen != observed) {
                            state.updateIosFullscreenLayout(observed)
                        }
                    }
                    if (transitions == 0) {
                        if (owner === active.owner) {
                            owner?.completeIosFullscreenRequest(active.generation, observed)
                        }
                        if (owner == null && active.owner == null && !active.target) restorePortrait = false
                        clearRequest()
                    }
                }
                if (request != null && (active.error != null || active.remainingSeconds <= 0.0)) {
                    if (owner === active.owner) {
                        owner?.let { state ->
                            state.completeIosFullscreenRequest(active.generation, observed ?: state.isFullscreen)
                        }
                    }
                    if (owner == null && active.owner == null) restorePortrait = false
                    clearRequest()
                    needsReconcile = owner !== active.owner ||
                        (owner != null && owner?.iosFullscreenGeneration != active.generation) ||
                        owner?.isChangingIosFullscreen == true
                }
                if (request != null) {
                    startTimer()
                    return
                }
            }

            if (transitions > 0) return
            val state = owner
            val target = state?.iosFullscreenTarget ?: false
            if (state == null && !restorePortrait) {
                stopObserving()
                return
            }
            if (!needsReconcile && state?.isChangingIosFullscreen != true) return
            if (observedLayout(state) == target) {
                state?.completeIosFullscreenRequest(state.iosFullscreenGeneration, target)
                needsReconcile = false
                if (state == null) {
                    restorePortrait = false
                    stopObserving()
                }
                return
            }
            val controller = host ?: return
            val scene = controller.view.window?.windowScene ?: return
            val needsRotation = observedLayout(null) != target
            val pending = Request(state, state?.iosFullscreenGeneration ?: 0L, target, needsRotation, now)
            request = pending
            needsReconcile = false
            if (needsRotation) {
                controller.setNeedsUpdateOfSupportedInterfaceOrientations()
                scene.requestGeometryUpdateWithPreferences(
                    UIWindowSceneGeometryPreferencesIOS(
                        if (target) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait
                    ),
                    errorHandler = { error ->
                        if (request === pending) pending.error = error?.localizedDescription ?: "rotation rejected"
                    },
                )
            }
            startTimer()
        } finally {
            reconciling = false
        }
    }

    private fun startTimer() {
        if (timer != null || request == null) return
        timer = scope?.launch {
            while (isActive && request != null) {
                delay(50)
                reconcile()
            }
        }
    }

    private fun clearRequest() {
        request = null
        timer?.cancel()
        timer = null
    }

    private fun stopObserving() {
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers.clear()
        scope?.cancel()
        scope = null
        timer = null
        sceneIsActive = null
    }
}
