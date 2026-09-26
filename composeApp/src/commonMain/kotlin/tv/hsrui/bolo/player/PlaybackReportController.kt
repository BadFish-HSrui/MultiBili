package tv.hsrui.bolo.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.PlaybackProgressReportMode
import tv.hsrui.bolo.player.base.BoloPlayerState
import tv.hsrui.network.feature.player.reportPlaybackProgress
import tv.hsrui.network.feature.player.reportPlaybackStart
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** 主线程持有观看状态；请求独立于页面的协程作用域，在退出时有界收尾。 */
class PlaybackReportController {
    private val settings: BoloSettings = getKoin().get()
    private val loginStorage: LoginStorage = getKoin().get()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requestMutex = Mutex()
    private var pendingRequests = 0
    private var closed = false
    private var media: Pair<Long, Long>? = null
    private var mediaGeneration = 0L
    private var acceptsPlayback = false
    private var hasPlayed = false
    private var positionMs = 0L
    private var wasEnded = false
    private var pageVisible = true
    private var foreground = true
    private var backgroundPlaybackAllowed = false
    private val playbackObservable get() = foreground || backgroundPlaybackAllowed
    private var countingPlayback = false
    private var playedTime = Duration.ZERO
    private var lastUpdate = TimeSource.Monotonic.markNow()
    private val pendingProgress = mutableSetOf<Pair<Long, Long>>()
    private var lastSuccessfulProgress: Pair<Long, Long>? = null
    private var immediateReportAccountSession: String? = null

    fun openMedia(avid: Long, cid: Long) {
        if (closed || media == (avid to cid)) return
        if (pageVisible && playbackObservable) reportProgress()
        advanceClock()
        mediaGeneration += 1
        media = (avid to cid).takeIf { avid > 0L && cid > 0L }
        acceptsPlayback = false
        hasPlayed = false
        positionMs = 0L
        wasEnded = false
        countingPlayback = false
        playedTime = Duration.ZERO
        lastSuccessfulProgress = null
        immediateReportAccountSession = loginStorage.cookies.sessData.takeIf {
            settings.playback.reportProgressImmediatelyEnabled && it.isNotEmpty()
        }
        reportStart()
    }

    fun beforeReload(playback: BoloPlayerState, backendAvailable: Boolean) {
        updatePlayback(playback, backendAvailable)
        acceptsPlayback = false
        countingPlayback = false
    }

    fun mediaLoaded() {
        if (!closed) acceptsPlayback = true
    }

    fun updatePlayback(playback: BoloPlayerState, backendAvailable: Boolean) {
        if (closed) return
        advanceClock()
        if (immediateReportAccountSession != loginStorage.cookies.sessData) immediateReportAccountSession = null
        // release 会把未确认的恢复目标保存到 state；无后端时保留最后的实际观看位置。
        val canObserve = acceptsPlayback && backendAvailable && pageVisible && playbackObservable &&
            playback.hasConfirmedPosition && !playback.isPlaybackSuspended && !playback.isRebuilding && !playback.isSeeking
        countingPlayback = canObserve && playback.isPlaying && !playback.isBuffering && !playback.isEnded
        if (countingPlayback) hasPlayed = true
        if (canObserve && !playback.isBuffering) {
            positionMs = playback.currentPositionMs.coerceAtLeast(0L)
            if (immediateReportAccountSession != null) {
                immediateReportAccountSession = null
                reportProgress(immediately = true)
            }
            if (playback.isEnded && !wasEnded) reportProgress()
            wasEnded = playback.isEnded
        }
        if (playedTime >= 60.seconds) {
            playedTime -= 60.seconds
            reportProgress()
        }
    }

    fun enterPage() {
        if (closed) return
        advanceClock()
        pageVisible = true
    }

    fun leavePage() {
        if (closed || !pageVisible) return
        advanceClock()
        if (playbackObservable) reportProgress()
        pageVisible = false
        countingPlayback = false
    }

    fun setBackgroundPlaybackAllowed(allowed: Boolean) {
        advanceClock()
        backgroundPlaybackAllowed = allowed
        countingPlayback = false
    }

    fun setForeground(active: Boolean) {
        if (closed || foreground == active) return
        advanceClock()
        // 在进入后台的回调内启动协程，iOS 可在挂起前申请后台执行时间。
        if (!active && pageVisible) reportProgress()
        foreground = active
        countingPlayback = false
    }

    private fun advanceClock() {
        val now = TimeSource.Monotonic.markNow()
        if (settings.playback.reportProgressMode == PlaybackProgressReportMode.EveryMinute) {
            if (countingPlayback) playedTime += now - lastUpdate
        } else {
            playedTime = Duration.ZERO
        }
        lastUpdate = now
    }

    private fun reportStart() {
        val target = media ?: return
        if (closed || !settings.playback.reportStartEnabled || !loginStorage.isLoggedIn) return
        val accountSession = loginStorage.cookies.sessData
        pendingRequests += 1
        requestScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withPlaybackReportBackgroundExecution {
                    requestMutex.withLock {
                        repeat(3) { attempt ->
                            if (!settings.playback.reportStartEnabled || !loginStorage.isLoggedIn ||
                                loginStorage.cookies.sessData != accountSession
                            ) return@withLock
                            try {
                                val result = withTimeoutOrNull(10_000L) {
                                    reportPlaybackStart(target.first, target.second)
                                }
                                if (result?.isSuccess == true) return@withLock
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // 上报失败静默处理，包含首次最多尝试三次。
                            }
                            if (attempt < 2) delay(1_000L)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 平台后台执行申请失败也保持静默。
            } finally {
                requestFinished()
            }
        }
    }

    private fun reportProgress(immediately: Boolean = false) {
        val target = media ?: return
        if (closed || !loginStorage.isLoggedIn) return
        if (immediately) {
            if (!settings.playback.reportProgressImmediatelyEnabled) return
        } else if (!hasPlayed || settings.playback.reportProgressMode == PlaybackProgressReportMode.Off) {
            return
        }
        val progressSeconds = positionMs / 1_000L
        val key = mediaGeneration to progressSeconds
        if (key == lastSuccessfulProgress || !pendingProgress.add(key)) return
        val accountSession = loginStorage.cookies.sessData
        pendingRequests += 1
        requestScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withPlaybackReportBackgroundExecution {
                    requestMutex.withLock {
                        repeat(3) { attempt ->
                            val enabled = if (immediately) {
                                settings.playback.reportProgressImmediatelyEnabled
                            } else {
                                settings.playback.reportProgressMode != PlaybackProgressReportMode.Off
                            }
                            if (!enabled || !loginStorage.isLoggedIn || loginStorage.cookies.sessData != accountSession
                            ) return@withLock
                            try {
                                val result = withTimeoutOrNull(10_000L) {
                                    reportPlaybackProgress(target.first, target.second, progressSeconds)
                                }
                                if (result?.isSuccess == true) {
                                    if (mediaGeneration == key.first) lastSuccessfulProgress = key
                                    return@withLock
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // 超时、网络异常及非零响应码均不影响播放和导航。
                            }
                            if (attempt < 2) delay(1_000L)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 平台后台执行申请失败也保持静默。
            } finally {
                pendingProgress.remove(key)
                requestFinished()
            }
        }
    }

    private fun requestFinished() {
        pendingRequests -= 1
        if (closed && pendingRequests == 0) requestScope.cancel()
    }

    fun close() {
        if (closed) return
        leavePage()
        closed = true
        if (pendingRequests == 0) {
            requestScope.cancel()
        } else {
            requestScope.launch {
                delay(10_000L)
                requestScope.cancel()
            }
        }
    }
}
