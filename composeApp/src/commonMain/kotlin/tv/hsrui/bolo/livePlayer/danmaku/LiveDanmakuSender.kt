package tv.hsrui.bolo.livePlayer.danmaku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuSendConfigData
import tv.hsrui.network.liveFeature.danmaku.fetchLiveDanmakuSendConfig
import tv.hsrui.network.liveFeature.danmaku.liveDanmakuLength
import tv.hsrui.network.liveFeature.danmaku.sendLiveDanmaku
import tv.hsrui.network.login.storage.LoginStorage

class LiveDanmakuSender internal constructor(
    private val scope: CoroutineScope,
    private val roomId: () -> Long?,
    private val configRequest: suspend (Long) -> LiveDanmakuSendConfigData = ::fetchLiveDanmakuSendConfig,
    private val sendRequest: suspend (Long, String) -> LiveDanmakuData = ::sendLiveDanmaku,
    val isLoggedIn: Boolean = getKoin().get<LoginStorage>().isLoggedIn,
) {
    var draft by mutableStateOf("")
        private set
    var isOpen by mutableStateOf(false)
        private set
    var isSending by mutableStateOf(false)
        private set
    var isLoadingConfig by mutableStateOf(false)
        private set
    var config by mutableStateOf<LiveDanmakuSendConfigData?>(null)
        private set
    var configError by mutableStateOf<String?>(null)
        private set
    var sendError by mutableStateOf<String?>(null)
        private set
    private var closed = false
    private var configJob: Job? = null
    private var sendJob: Job? = null
    private val mutableSentMessages = MutableSharedFlow<LiveDanmakuData>()
    val sentMessages = mutableSentMessages.asSharedFlow()
    val length: Int get() = draft.liveDanmakuLength()
    val isTooLong: Boolean get() = config?.let { length > it.maxLength } == true
    val canSend: Boolean get() = !closed && isLoggedIn && isOpen && !isSending &&
        config != null && !isLoadingConfig && draft.isNotBlank() && !isTooLong

    fun open() {
        if (closed || !isLoggedIn || (roomId() ?: 0) <= 0) return
        isOpen = true
        if (config == null && !isLoadingConfig) loadConfig()
    }

    fun dismiss() { isOpen = false }

    fun updateDraft(value: String) {
        if (closed || isSending || !isLoggedIn) return
        draft = value
        sendError = null
    }

    fun loadConfig() {
        val targetRoom = roomId()?.takeIf { it > 0 } ?: return
        if (closed || !isLoggedIn || isLoadingConfig || isSending) return
        isLoadingConfig = true
        config = null
        configError = null
        configJob = scope.launch {
            try {
                val result = configRequest(targetRoom)
                ensureActive()
                config = result
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
                configError = "发送配置加载超时"
            } catch (error: Exception) {
                configError = error.message ?: "发送配置加载失败"
            } finally {
                isLoadingConfig = false
            }
        }
    }

    fun send() {
        if (!canSend) return
        val targetRoom = roomId() ?: return
        val message = draft
        val sendConfig = config
        val sender = sendConfig?.user
        isSending = true
        sendError = null
        sendJob = scope.launch {
            try {
                val result = sendRequest(targetRoom, message)
                ensureActive()
                val user = result.user
                val echo = result.copy(
                    bubbleColorArgb = result.bubbleColorArgb ?: sendConfig?.bubbleColorArgb,
                    bubbleId = result.bubbleId ?: sendConfig?.bubbleId,
                    user = user.copy(
                        uid = user.uid ?: sender?.uid,
                        isRoomAdmin = user.isRoomAdmin ?: sender?.isRoomAdmin,
                        isMystery = user.isMystery ?: sender?.isMystery,
                        guardLevel = user.guardLevel ?: sender?.guardLevel,
                        wealthLevel = user.wealthLevel ?: sender?.wealthLevel,
                        userLevel = user.userLevel ?: sender?.userLevel,
                    ),
                )
                mutableSentMessages.emit(echo)
                draft = ""
                isOpen = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                sendError = error.message ?: "结果未确认，请勿重复发送"
            } finally {
                isSending = false
            }
        }
    }

    fun close() {
        closed = true
        isOpen = false
        configJob?.cancel()
        sendJob?.cancel()
    }
}
