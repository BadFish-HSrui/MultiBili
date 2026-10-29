package tv.hsrui.bolo.livePlayer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.setting.live.LiveHistoryReportMode
import tv.hsrui.bolo.boloSetting.setting.live.LiveSettings
import tv.hsrui.bolo.player.withPlaybackReportBackgroundExecution
import tv.hsrui.network.liveFeature.room.reportLiveHistory
import tv.hsrui.network.liveFeature.room.reportLiveRoomEntry
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.BaseResponse

/** 主线程接收页面进出；上报请求独立于页面作用域，有界完成退出收尾。 */
class LiveReportController internal constructor(
    private val settings: LiveSettings = getKoin().get<BoloSettings>().live,
    private val isLoggedIn: () -> Boolean = { getKoin().get<LoginStorage>().isLoggedIn },
    private val historyRequest: suspend (Long) -> BaseResponse = ::reportLiveHistory,
    private val roomEntryRequest: suspend (Long) -> BaseResponse = ::reportLiveRoomEntry,
) {
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pendingRequests = 0
    private var roomId: Long? = null
    private var pageVisible = false
    private var enteredRoom = false
    private var closed = false

    fun setRoom(roomId: Long) {
        if (closed) return
        require(roomId > 0)
        this.roomId = roomId
        if (!pageVisible || enteredRoom) return
        enteredRoom = true
        if (settings.reportHistoryMode == LiveHistoryReportMode.OnEnter) reportHistory(roomId)
        reportRoomEntry(roomId)
    }

    fun enterPage() {
        if (closed || pageVisible) return
        pageVisible = true
        enteredRoom = false
        roomId?.let(::setRoom)
    }

    fun leavePage() {
        if (closed || !pageVisible) return
        pageVisible = false
        if (enteredRoom && settings.reportHistoryMode == LiveHistoryReportMode.OnExit) {
            roomId?.let(::reportHistory)
        }
    }

    private fun reportHistory(roomId: Long) {
        if (!isLoggedIn() || settings.reportHistoryMode == LiveHistoryReportMode.Off) return
        pendingRequests++
        requestScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withTimeoutOrNull(10_000L) {
                    withPlaybackReportBackgroundExecution {
                        check(historyRequest(roomId).isSuccess)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 上报失败不影响播放和导航，也不自动重试。
            } finally {
                requestFinished()
            }
        }
    }

    private fun reportRoomEntry(roomId: Long) {
        if (!isLoggedIn() || !settings.reportRoomEntryEnabled) return
        pendingRequests++
        requestScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withTimeoutOrNull(10_000L) {
                    withPlaybackReportBackgroundExecution {
                        check(roomEntryRequest(roomId).isSuccess)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 避免一次进房因重试重复触发提醒。
            } finally {
                requestFinished()
            }
        }
    }

    private fun requestFinished() {
        pendingRequests--
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
