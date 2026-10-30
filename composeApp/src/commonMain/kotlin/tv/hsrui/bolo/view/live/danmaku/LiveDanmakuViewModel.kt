package tv.hsrui.bolo.view.live.danmaku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuBubbleData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMedalResourcesData
import tv.hsrui.network.liveFeature.danmaku.fetchLiveDanmakuBubbleResources
import tv.hsrui.network.liveFeature.danmaku.fetchLiveDanmakuHistory
import tv.hsrui.network.liveFeature.danmaku.fetchLiveDanmakuMedalResources
import tv.hsrui.network.liveFeature.danmaku.parseLiveDanmakuMessage
import tv.hsrui.network.liveFeature.message.LiveMessageData
import tv.hsrui.network.login.storage.LoginStorage

private const val MAX_DANMAKU_ITEMS = 1_000

data class LiveDanmakuListItem(val key: Long, val sequence: Long, val data: LiveDanmakuData)

data class LiveDanmakuUiState(
    val session: Long = 0,
    val items: List<LiveDanmakuListItem> = emptyList(),
    val latestSequence: Long = 0,
    val readSequence: Long = 0,
    val medalResources: LiveDanmakuMedalResourcesData = LiveDanmakuMedalResourcesData(),
    val bubbleResources: Map<Long, LiveDanmakuBubbleData> = emptyMap(),
) {
    val unreadCount: Long get() = latestSequence - readSequence
}

class LiveDanmakuViewModel internal constructor(
    private val roomId: Long,
    messages: Flow<LiveMessageData>,
    isCurrentMessage: (Long, Long) -> Boolean,
    private val historyRequest: suspend (Long) -> List<LiveDanmakuData> = ::fetchLiveDanmakuHistory,
    accountChanges: Flow<Long> = getKoin().get<LoginStorage>().currentUserMidFlow,
    sentMessages: Flow<LiveDanmakuData> = emptyFlow(),
    medalResourcesRequest: suspend () -> LiveDanmakuMedalResourcesData = ::fetchLiveDanmakuMedalResources,
    bubbleResourcesRequest: suspend () -> Map<Long, LiveDanmakuBubbleData> = ::fetchLiveDanmakuBubbleResources,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LiveDanmakuUiState())
    val uiState = mutableState.asStateFlow()
    private var visible = false

    init {
        viewModelScope.launch {
            try {
                val resources = medalResourcesRequest()
                ensureActive()
                mutableState.value = mutableState.value.copy(medalResources = resources)
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
            } catch (_: Exception) {
                // 资源不可用时继续显示粉丝勋章文字。
            }
        }
        viewModelScope.launch {
            try {
                val resources = bubbleResourcesRequest()
                ensureActive()
                mutableState.value = mutableState.value.copy(bubbleResources = resources)
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
            } catch (_: Exception) {
                // 气泡资源不影响消息接收。
            }
        }
        viewModelScope.launch {
            accountChanges.distinctUntilChanged().collectLatest {
                val session = mutableState.value.session + 1
                mutableState.value = LiveDanmakuUiState(
                    session = session,
                    medalResources = mutableState.value.medalResources,
                    bubbleResources = mutableState.value.bubbleResources,
                )
                coroutineScope {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        sentMessages.collect { append(it, session) }
                    }
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        messages.collect { message ->
                            if (message.roomId == roomId && isCurrentMessage(roomId, message.generation)) {
                                parseLiveDanmakuMessage(message)?.let { append(it, session, updateSnapshot = true) }
                            }
                        }
                    }
                    launch {
                        try {
                            val history = historyRequest(roomId)
                            ensureActive()
                            if (mutableState.value.session == session) prependHistory(history)
                        } catch (error: CancellationException) {
                            if (error !is TimeoutCancellationException) throw error
                        } catch (_: Exception) {
                            // 历史不可用时仍保留实时订阅。
                        }
                    }
                }
            }
        }
    }

    fun setVisible(value: Boolean) { visible = value }

    fun markVisible(session: Long, keys: Set<Long>) {
        val state = mutableState.value
        if (!visible || state.session != session) return
        val sequence = state.items.asSequence()
            .filter { it.key in keys }
            .maxOfOrNull { it.sequence } ?: return
        if (sequence > state.readSequence) mutableState.value = state.copy(readSequence = sequence)
    }

    private fun append(data: LiveDanmakuData, session: Long, updateSnapshot: Boolean = false) {
        val state = mutableState.value
        if (state.session != session) return
        val existing = if (data.id.isNotEmpty()) state.items.indexOfFirst { it.data.id == data.id } else -1
        if (existing >= 0) {
            if (!updateSnapshot) return
            val item = state.items[existing]
            val updated = item.data.copy(
                user = data.user,
                spaceUrl = data.spaceUrl,
                bubbleColorArgb = data.bubbleColorArgb,
                bubbleId = data.bubbleId,
            )
            if (item.data != updated) {
                mutableState.value = state.copy(items = state.items.toMutableList().apply {
                    set(existing, item.copy(data = updated))
                })
            }
            return
        }
        val sequence = state.latestSequence + 1
        mutableState.value = state.copy(
            items = (state.items + LiveDanmakuListItem(sequence, sequence, data)).takeLast(MAX_DANMAKU_ITEMS),
            latestSequence = sequence,
        )
    }

    private fun prependHistory(history: List<LiveDanmakuData>) {
        val state = mutableState.value
        val ids = state.items.mapNotNullTo(mutableSetOf()) { it.data.id.takeIf(String::isNotEmpty) }
        val sorted = history.sortedBy { it.sentAtEpochMillis ?: Long.MIN_VALUE }
            .filter { it.id.isEmpty() || ids.add(it.id) }
        val items = sorted.mapIndexed { index, data ->
            LiveDanmakuListItem(index.toLong() - sorted.size, 0, data)
        }
        mutableState.value = state.copy(items = (items + state.items).takeLast(MAX_DANMAKU_ITEMS))
    }
}
