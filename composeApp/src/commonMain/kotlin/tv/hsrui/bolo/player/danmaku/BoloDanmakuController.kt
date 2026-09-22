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
    private var awaitingSeekSync = false
    private var layerOwner: Any? = null
    internal var isDisposed = false
        private set

    /** 替换待入场数据；已入场条目持有独立快照，直到自然结束或显式清屏。 */
    fun load(items: List<BoloDanmakuItem>) {
        checkUsable()
        engine.load(items)
        if (!awaitingSeekSync) engine.advance(_state.value.positionMs)
        publish(_state.value.copy(itemCount = engine.itemCount))
    }

    fun append(items: List<BoloDanmakuItem>) {
        checkUsable()
        engine.append(items)
        if (!awaitingSeekSync) engine.advance(_state.value.positionMs)
        publish(_state.value.copy(itemCount = engine.itemCount))
    }

    /** 保留原始进度供回看，当前帧直接入场，不依赖媒体调度游标。 */
    fun showImmediately(item: BoloDanmakuItem) {
        checkUsable()
        engine.showImmediately(item)
        publish(_state.value.copy(itemCount = engine.itemCount))
    }

    fun play() {
        checkUsable()
        awaitingSeekSync = false
        engine.advance(_state.value.positionMs)
        updatePlayback(true, _state.value.playbackSpeed)
    }

    fun pause() {
        checkUsable()
        updatePlayback(false, _state.value.playbackSpeed)
    }

    /** 显式 Seek 清屏并定位待入场游标；下一次同步或 play 后允许新弹幕入场。 */
    fun seekToMs(positionMs: Long) {
        checkUsable()
        val position = positionMs.coerceAtLeast(0)
        engine.seek(position)
        awaitingSeekSync = true
        publish(_state.value.copy(positionMs = position))
    }

    fun setPlaybackSpeed(speed: Float) {
        checkUsable()
        require(speed.isFinite() && speed > 0f)
        updatePlayback(_state.value.isPlaying, speed)
    }

    /** 外部位置只用于入场；discontinuity 仅表示明确的 Seek。 */
    fun syncPlayback(
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float,
        discontinuity: Boolean = false,
    ) {
        checkUsable()
        require(speed.isFinite() && speed > 0f)
        val position = positionMs.coerceAtLeast(0)
        if (discontinuity || awaitingSeekSync) engine.seek(position)
        awaitingSeekSync = false
        engine.advance(position)
        updatePlayback(isPlaying, speed, position)
    }

    fun setVisible(visible: Boolean) {
        checkUsable()
        if (_state.value.isVisible != visible) publish(_state.value.copy(isVisible = visible))
    }

    fun clear() {
        checkUsable()
        engine.clear()
        animationBaseMs = 0.0
        animationAnchor = TimeSource.Monotonic.markNow()
        awaitingSeekSync = false
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
