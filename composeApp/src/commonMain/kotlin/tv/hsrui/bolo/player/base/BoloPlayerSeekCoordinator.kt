package tv.hsrui.bolo.player.base

import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 统一维护 seek 的 generation、latest-wins revision、提交次数和时间确认窗口。
 *
 * 调用方必须使用独立锁串行访问，并负责 native 提交、超时任务和状态发布。
 */
internal class BoloPlayerSeekCoordinator {
    private var mediaGeneration = 0L
    private var seekRevision = 0L
    private var targetPositionMs: Long? = null
    private var submittedAt: TimeMark? = null
    private var submitted = false
    private var attempts = 0

    internal val currentMediaGeneration: Long get() = mediaGeneration
    internal val currentRevision: Long get() = seekRevision
    internal val pendingPositionMs: Long? get() = targetPositionMs
    internal val submittedAttempt: Int get() = attempts
    internal val isSubmitted: Boolean get() = submitted

    /** 切换媒体并取消旧媒体下的所有请求和回调。 */
    internal fun onMediaChanged(): Long {
        mediaGeneration += 1L
        clearRequest()
        return mediaGeneration
    }

    /** 创建 latest-wins 请求；0ms 是合法目标。 */
    internal fun requestSeek(positionMs: Long): Long {
        seekRevision += 1L
        targetPositionMs = positionMs.coerceAtLeast(0L)
        submittedAt = null
        submitted = false
        attempts = 0
        return seekRevision
    }

    /** 记录一次成功交给 native 层的提交及其确认窗口起点。 */
    internal fun markSubmitted(revision: Long): Boolean {
        if (!isCurrent(revision) || attempts >= MaxAttempts) return false
        attempts += 1
        submittedAt = TimeSource.Monotonic.markNow()
        submitted = true
        return true
    }

    /**
     * 接受 native 时间观测。无 pending 时所有非负时间都可信；有 pending 时仅接受确认窗口内的时间。
     * 返回 true 时调用方可以写入 actual；若此前有 pending，同时应清除公开 pending 状态。
     */
    internal fun acceptObservedPosition(
        positionMs: Long,
        isPlaying: Boolean,
        playbackRate: Float
    ): Boolean {
        if (positionMs < 0L) return false
        val targetMs = targetPositionMs ?: return true
        val mark = submittedAt ?: return false
        if (!submitted) return false

        val lowerBoundMs = (targetMs - ConfirmationToleranceMs).coerceAtLeast(0L)
        val playbackAdvanceMs = if (isPlaying) {
            val elapsedMs = mark.elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
            val rate = playbackRate.takeIf { it.isFinite() && it > 0f } ?: 1f
            (elapsedMs.toDouble() * rate.toDouble())
                .coerceAtMost(Long.MAX_VALUE.toDouble())
                .toLong()
        } else {
            0L
        }
        val upperBoundMs = saturatingAdd(
            saturatingAdd(targetMs, playbackAdvanceMs),
            ConfirmationToleranceMs
        )
        if (positionMs !in lowerBoundMs..upperBoundMs) return false

        clearRequest()
        return true
    }

    internal fun isCurrent(mediaGeneration: Long, revision: Long): Boolean =
        this.mediaGeneration == mediaGeneration && isCurrent(revision)

    internal fun canRetry(revision: Long): Boolean =
        isCurrent(revision) && attempts < MaxAttempts

    /** 只取消指定 revision，避免旧超时撤销新请求。 */
    internal fun cancelSeek(revision: Long): Boolean {
        if (!isCurrent(revision)) return false
        clearRequest()
        return true
    }

    internal fun cancelCurrentSeek(): Boolean {
        if (targetPositionMs == null) return false
        clearRequest()
        return true
    }

    private fun isCurrent(revision: Long): Boolean =
        targetPositionMs != null && seekRevision == revision

    private fun clearRequest() {
        targetPositionMs = null
        submittedAt = null
        submitted = false
        attempts = 0
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    internal companion object {
        internal const val ConfirmationToleranceMs = 250L
        internal const val ConfirmationReadbackIntervalMs = 50L
        internal const val ConfirmationReadbackAttempts = 20
        internal const val AttemptTimeoutMs = 10_000L
        internal const val MaxAttempts = 2
    }
}
