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
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIDeviceOrientation.UIDeviceOrientationLandscapeLeft
import platform.UIKit.UIDeviceOrientation.UIDeviceOrientationLandscapeRight
import platform.UIKit.UIDeviceOrientation.UIDeviceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskAllButUpsideDown
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneActivationStateForegroundInactive
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations
import platform.darwin.NSObjectProtocol
import kotlin.math.abs
import kotlin.native.ref.WeakReference
import tv.hsrui.bolo.LocalStatusBarAppearance
import tv.hsrui.bolo.getPlatform

internal val LocalIosPlayerFullscreenCoordinator = staticCompositionLocalOf<IosPlayerFullscreenCoordinator> {
    error("Missing iOS fullscreen coordinator")
}

@Composable
actual fun PlayerFullscreenEffect(fullscreenState: PlayerFullscreenState, autoFullscreenOnRotateEnabled: Boolean) {
    if (!fullscreenState.isPhone) {
        val appearance = LocalStatusBarAppearance.current
        val hidden = fullscreenState.isFullscreen && !fullscreenState.hasVisibleControls
        DisposableEffect(appearance, fullscreenState) {
            appearance.bindPlayer(fullscreenState, hidden)
            onDispose { appearance.unbindPlayer(fullscreenState) }
        }
        SideEffect { appearance.updatePlayer(fullscreenState, hidden) }
        // 平板不绑定方向请求，也不在离页时恢复竖屏。
        return
    }
    val coordinator = LocalIosPlayerFullscreenCoordinator.current
    val viewport = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    DisposableEffect(coordinator, fullscreenState) {
        coordinator.bind(fullscreenState, autoFullscreenOnRotateEnabled)
        onDispose { coordinator.unbind(fullscreenState) }
    }
    SideEffect {
        coordinator.updateAutoRotate(fullscreenState, autoFullscreenOnRotateEnabled)
        coordinator.updateViewport(fullscreenState, viewport, density)
    }
}

// 一个宿主串行协调窗口请求；页面只提交目标，原生几何和 Compose 视口共同确认展示状态。
internal class IosPlayerFullscreenCoordinator {
    private val isPhone = getPlatform().isPhone
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
        val userInitiated: Boolean,
        var checkedAt: Double,
    ) {
        var remainingSeconds = 3.0
        var wasForeground = true
        var error: String? = null
    }

    private val owners = mutableListOf<PlayerFullscreenState>()
    private val viewports = mutableMapOf<PlayerFullscreenState, Viewport>()
    private val autoRotate = mutableMapOf<PlayerFullscreenState, Boolean>()
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
    private var orientationObserver: NSObjectProtocol? = null
    private var appliedOrientations: UIInterfaceOrientationMask? = null

    val supportedOrientations: UIInterfaceOrientationMask
        get() {
            if (!isPhone) return UIInterfaceOrientationMaskAll
            val state = owner
            if (state == null) return UIInterfaceOrientationMaskPortrait
            val target = request?.target ?: state.iosFullscreenTarget
            return if (request != null || state.isChangingIosFullscreen ||
                state.manualOrientationTarget != null || autoRotate[state] != true
            ) {
                if (target) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait
            } else UIInterfaceOrientationMaskAllButUpsideDown
        }

    private fun updateOrientationPolicy() {
        val mask = supportedOrientations
        if (appliedOrientations == mask) return
        appliedOrientations = mask
        host?.setNeedsUpdateOfSupportedInterfaceOrientations()
        host?.view?.window?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
    }

    private fun releaseManualOrientation() {
        val state = owner ?: return
        if (!isForeground() || autoRotate[state] != true || request != null || transitions > 0) return
        val landscape = when (UIDevice.currentDevice.orientation) {
            UIDeviceOrientationLandscapeLeft, UIDeviceOrientationLandscapeRight -> true
            UIDeviceOrientationPortrait -> false
            else -> return
        }
        if (observedLayout(state) == landscape) {
            state.releaseManualOrientationTarget(landscape)
            updateOrientationPolicy()
        }
    }

    private fun updateOrientationObservation() {
        val shouldObserve = isPhone && owner != null && autoRotate[owner] == true && isForeground()
        if (shouldObserve && orientationObserver == null) {
            UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
            orientationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                UIDeviceOrientationDidChangeNotification, UIDevice.currentDevice, NSOperationQueue.mainQueue,
            ) { releaseManualOrientation() }
        } else if (!shouldObserve && orientationObserver != null) {
            NSNotificationCenter.defaultCenter.removeObserver(orientationObserver!!)
            orientationObserver = null
            UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        }
    }

    fun bind(state: PlayerFullscreenState, autoFullscreenOnRotateEnabled: Boolean) {
        if (!isPhone) return
        if (state in owners) return
        owners.add(state)
        autoRotate[state] = autoFullscreenOnRotateEnabled
        restorePortrait = false
        needsReconcile = true
        state.onIosFullscreenRequest = {
            if (owner === state) {
                needsReconcile = true
                updateOrientationPolicy()
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
        updateOrientationPolicy()
        reconcile()
    }

    fun updateAutoRotate(state: PlayerFullscreenState, enabled: Boolean) {
        if (state !in owners || autoRotate[state] == enabled) return
        autoRotate[state] = enabled
        if (owner === state) {
            needsReconcile = true
            updateOrientationPolicy()
            reconcile()
        }
    }

    fun unbind(state: PlayerFullscreenState) {
        if (!owners.remove(state)) return
        state.onIosFullscreenRequest = null
        viewports.remove(state)
        autoRotate.remove(state)
        restorePortrait = owners.isEmpty()
        needsReconcile = true
        updateOrientationPolicy()
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

    fun transitionStarted(landscape: Boolean) {
        transitions++
        val state = owner ?: return
        if (!isSceneInForeground() || request != null || autoRotate[state] != true) return
        // 使用 UIKit 已允许的目标方向，在系统转场内同步布局；手动请求与防回弹仍由状态层保护。
        state.updateFullscreenFromRotation(landscape)
    }

    fun transitionFinished() {
        transitions = (transitions - 1).coerceAtLeast(0)
        reconcile()
    }

    private fun isForeground(): Boolean =
        sceneIsActive != false &&
            host?.view?.window?.windowScene?.activationState == UISceneActivationStateForegroundActive

    private fun isSceneInForeground(): Boolean =
        when (host?.view?.window?.windowScene?.activationState) {
            UISceneActivationStateForegroundActive, UISceneActivationStateForegroundInactive -> true
            else -> false
        }

    private fun syncAutomaticLayout() {
        val state = owner ?: return
        if (!isSceneInForeground() || request != null || transitions > 0 || autoRotate[state] != true) return
        observedLayout(state)?.let(state::updateFullscreenFromRotation)
    }

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
            updateOrientationObservation()
            val now = CACurrentMediaTime()
            request?.let {
                if (it.wasForeground) it.remainingSeconds -= now - it.checkedAt
                it.checkedAt = now
                it.wasForeground = foreground
            }
            if (!foreground) {
                // 控制中心等前台失活期间仍跟随实际几何；方向请求和超时继续暂停。
                syncAutomaticLayout()
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
                            // 策略恢复失败不能把关闭开关后的横屏误判为自动全屏。
                            val fallback = if (active.userInitiated) observed ?: state.isFullscreen else state.isFullscreen
                            state.completeIosFullscreenRequest(active.generation, fallback)
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
            releaseManualOrientation()
            val state = owner
            val target = state?.iosFullscreenTarget ?: false
            if (state == null && !restorePortrait) {
                stopObserving()
                return
            }
            if (state != null && !state.isChangingIosFullscreen && autoRotate[state] == true) {
                // 首次布局、回前台及转场收尾只校准实际几何，不追逐旋转前的全屏目标。
                syncAutomaticLayout()
                needsReconcile = false
                updateOrientationPolicy()
                return
            }
            if (!needsReconcile && state?.isChangingIosFullscreen != true) return
            if (observedLayout(state) == target) {
                state?.completeIosFullscreenRequest(state.iosFullscreenGeneration, target)
                needsReconcile = false
                releaseManualOrientation()
                updateOrientationPolicy()
                if (state == null) {
                    restorePortrait = false
                    stopObserving()
                }
                return
            }
            val controller = host ?: return
            val scene = controller.view.window?.windowScene ?: return
            val nativeLayout = observedLayout(null) ?: return
            val needsRotation = nativeLayout != target
            val pending = Request(
                state, state?.iosFullscreenGeneration ?: 0L, target, needsRotation,
                state?.isChangingIosFullscreen == true, now,
            )
            request = pending
            needsReconcile = false
            updateOrientationPolicy()
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
        updateOrientationPolicy()
    }

    private fun stopObserving() {
        orientationObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
            UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
        }
        orientationObserver = null
        observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
        observers.clear()
        scope?.cancel()
        scope = null
        timer = null
        sceneIsActive = null
        updateOrientationPolicy()
    }
}
