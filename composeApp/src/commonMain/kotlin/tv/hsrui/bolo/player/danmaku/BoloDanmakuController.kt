package tv.hsrui.bolo.player.danmaku

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.TimeSource

/** 主线程串行调用；一个控制器同时只能挂载一个 BoloDanmakuLayer。 */
class BoloDanmakuController {
    private val _state = MutableStateFlow(BoloDanmakuState())
    val state: StateFlow<BoloDanmakuState> = _state.asStateFlow()
    private val _changes = MutableStateFlow(0L)
    internal val changes = _changes.asStateFlow()
    internal val engine = BoloDanmakuEngine()
    private var animationAnchor = TimeSource.Monotonic.markNow()
    private var animationBaseMs = 0.0
    private var pendingSeekPositionMs: Long? = null
    private var schedulingSuspended = true
    private var needsRestore = true
    private var sampleAnimationTimeMs = 0L
    private var layerOwner: Any? = null
    internal var isDisposed = false
        private set

    /** 更新待入场缓存并补齐当前有效窗口；已入场条目持有独立快照。 */
    fun load(items: List<BoloDanmakuItem>) {
        checkUsable()
        engine.load(items)
        publish(_state.value.copy(itemCount = engine.itemCount))
    }

    /** 保留原始进度供回看，当前帧直接入场，不依赖媒体调度游标。 */
    fun showImmediately(item: BoloDanmakuItem) {
        checkUsable()
        engine.showImmediately(item)
        publish(_state.value.copy(itemCount = engine.itemCount))
    }

    fun pause() {
        checkUsable()
        updatePlayback(false, _state.value.playbackSpeed)
    }

    /** 登记目标并冻结当前画面；位置确认后才恢复目标时刻的弹幕。 */
    fun seekToMs(positionMs: Long) {
        checkUsable()
        pendingSeekPositionMs = positionMs.coerceAtLeast(0)
        pause()
        engine.suspendScheduling()
    }

    /** 仅接收可信的实际位置；普通采样不重建在屏条目。 */
    fun syncPlayback(
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float,
        discontinuity: Boolean = false,
    ) {
        checkUsable()
        require(speed.isFinite() && speed > 0f)
        val position = positionMs.coerceAtLeast(0)
        sampleAnimationTimeMs = animationTimeMs()
        if (_state.value.isVisible) {
            if (discontinuity || pendingSeekPositionMs != null || schedulingSuspended || needsRestore) {
                engine.seek(position, sampleAnimationTimeMs, speed)
            } else engine.advance(position, sampleAnimationTimeMs, speed)
            needsRestore = false
        } else needsRestore = true
        pendingSeekPositionMs = null
        schedulingSuspended = false
        updatePlayback(isPlaying, speed, position)
    }

    fun setVisible(visible: Boolean) {
        checkUsable()
        if (_state.value.isVisible == visible) return
        if (visible && !schedulingSuspended && pendingSeekPositionMs == null) {
            engine.seek(_state.value.positionMs, sampleAnimationTimeMs, _state.value.playbackSpeed)
            needsRestore = false
        } else {
            engine.suspendScheduling()
            needsRestore = true
        }
        publish(_state.value.copy(isVisible = visible))
    }

    internal fun suspendPlayback() {
        pause()
        engine.suspendScheduling()
        schedulingSuspended = true
        needsRestore = true
    }

    fun clear() {
        checkUsable()
        engine.clear()
        animationBaseMs = 0.0
        animationAnchor = TimeSource.Monotonic.markNow()
        pendingSeekPositionMs = null
        schedulingSuspended = true
        needsRestore = true
        sampleAnimationTimeMs = 0L
        publish(BoloDanmakuState(isVisible = _state.value.isVisible, playbackSpeed = _state.value.playbackSpeed))
    }

    fun dispose() {
        if (isDisposed) return
        clear()
        isDisposed = true
        layerOwner = null
        _changes.value += 1
    }

    internal fun attach(owner: Any) {
        checkUsable()
        check(layerOwner == null || layerOwner === owner) { "弹幕控制器已绑定其他显示层" }
        layerOwner = owner
        engine.requestLayout()
    }

    internal fun detach(owner: Any) {
        if (layerOwner !== owner) return
        layerOwner = null
    }

    /** 与媒体位置无关的单调动画时间，仅暂停与倍速控制其推进。 */
    internal fun animationTimeMs(): Long = animationTime().coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()

    private fun animationTime(): Double = animationBaseMs + if (_state.value.isPlaying) {
        animationAnchor.elapsedNow().inWholeNanoseconds / 1_000_000.0 *
            danmakuAdvanceFactor(_state.value.playbackSpeed)
    } else 0.0

    private fun updatePlayback(isPlaying: Boolean, speed: Float, positionMs: Long = _state.value.positionMs) {
        val previous = _state.value
        if (previous.isPlaying != isPlaying || previous.playbackSpeed != speed) {
            animationBaseMs = animationTime()
            animationAnchor = TimeSource.Monotonic.markNow()
        }
        publish(previous.copy(positionMs = positionMs, isPlaying = isPlaying, playbackSpeed = speed))
    }

    private fun publish(state: BoloDanmakuState) {
        _state.value = state
        _changes.value += 1
    }

    private fun checkUsable() = check(!isDisposed) { "弹幕控制器已释放" }
}
