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
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.danmaku.fetchLiveDanmakuHistory
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
) {
    val unreadCount: Long get() = latestSequence - readSequence
}

class LiveDanmakuViewModel internal constructor(
    private val roomId: Long,
    messages: Flow<LiveMessageData>,
    isCurrentMessage: (Long, Long) -> Boolean,
    private val historyRequest: suspend (Long) -> List<LiveDanmakuData> = ::fetchLiveDanmakuHistory,
    accountChanges: Flow<Long> = getKoin().get<LoginStorage>().currentUserMidFlow,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LiveDanmakuUiState())
    val uiState = mutableState.asStateFlow()
    private var visible = false

    init {
        viewModelScope.launch {
            accountChanges.distinctUntilChanged().collectLatest {
                val session = mutableState.value.session + 1
                mutableState.value = LiveDanmakuUiState(session = session)
                coroutineScope {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        messages.collect { message ->
                            if (message.roomId == roomId && isCurrentMessage(roomId, message.generation)) {
                                parseLiveDanmakuMessage(message)?.let { append(it, session) }
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

    private fun append(data: LiveDanmakuData, session: Long) {
        val state = mutableState.value
        if (state.session != session || data.id.isNotEmpty() && state.items.any { it.data.id == data.id }) return
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
