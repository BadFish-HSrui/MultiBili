package tv.hsrui.bolo.view.live.superChat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.liveFeature.message.LiveMessageData
import tv.hsrui.network.liveFeature.superChat.LiveSuperChatData
import tv.hsrui.network.liveFeature.superChat.parseLiveSuperChatDeletedIds
import tv.hsrui.network.liveFeature.superChat.parseLiveSuperChatMessage
import kotlin.time.Clock

internal const val SUPER_CHAT_ANIMATION_MILLIS = 250

data class LiveSuperChatItem(
    val data: LiveSuperChatData,
    val expiresAtMillis: Long,
    val remainingSeconds: Int,
    val enteredAtMillis: Long,
    val animateEntry: Boolean = false,
) {
    val progress: Float get() = (remainingSeconds.toFloat() / data.totalSeconds).coerceIn(0f, 1f)
}

data class LiveSuperChatUiState(
    val items: List<LiveSuperChatItem> = emptyList(),
    val selectedId: String? = null,
    val isExpanded: Boolean = false,
    val isManualSelection: Boolean = false,
) {
    val selectedItem: LiveSuperChatItem? get() = items.firstOrNull { it.data.id == selectedId }
}

class LiveSuperChatViewModel internal constructor(
    private val roomId: Long,
    messages: Flow<LiveMessageData>,
    private val isCurrentMessage: (Long, Long) -> Boolean,
    initialMessages: List<LiveSuperChatData> = emptyList(),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(LiveSuperChatUiState())
    val uiState = mutableState.asStateFlow()
    private val seenIds = mutableSetOf<String>()
    private val pending = ArrayDeque<String>()
    private var visible = false
    private var ticker: Job? = null
    private var displayJob: Job? = null

    init {
        mergeInitial(initialMessages)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            messages.collect { message ->
                if (message.roomId != roomId || !isCurrentMessage(roomId, message.generation)) return@collect
                val deleted = parseLiveSuperChatDeletedIds(message)
                if (deleted.isNotEmpty()) {
                    seenIds.addAll(deleted)
                    remove(deleted)
                }
                parseLiveSuperChatMessage(message)?.let { append(it, animate = visible) }
            }
        }
    }

    fun mergeInitial(messages: List<LiveSuperChatData>) {
        refreshTime()
        messages.sortedBy { it.endTimeEpochSeconds?.minus(it.totalSeconds) ?: 0L }
            .forEach { append(it, animate = false) }
    }

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        refreshTime()
        if (!value) {
            displayJob?.cancel()
            displayJob = null
            pending.clear()
            mutableState.value = mutableState.value.copy(
                items = mutableState.value.items.map { it.copy(animateEntry = false) },
                selectedId = null,
                isExpanded = false,
                isManualSelection = false,
            )
        }
    }

    fun finishEntry(id: String) {
        val state = mutableState.value
        if (state.items.none { it.data.id == id && it.animateEntry }) return
        mutableState.value = state.copy(items = state.items.map {
            if (it.data.id == id) it.copy(animateEntry = false) else it
        })
    }

    fun toggle(id: String) {
        refreshTime()
        val state = mutableState.value
        if (!visible || state.items.none { it.data.id == id }) return
        displayJob?.cancel()
        pending.removeAll { it == id }
        if (state.selectedId == id && state.isManualSelection) {
            mutableState.value = state.copy(isExpanded = false, isManualSelection = false)
            displayJob = viewModelScope.launch {
                delay(SUPER_CHAT_ANIMATION_MILLIS.toLong())
                mutableState.value = mutableState.value.copy(selectedId = null)
                displayJob = null
                showNext()
            }
        } else {
            displayJob = null
            mutableState.value = state.copy(selectedId = id, isExpanded = true, isManualSelection = true)
        }
    }

    private fun append(data: LiveSuperChatData, animate: Boolean) {
        if (!seenIds.add(data.id)) return
        val now = nowMillis()
        val lifetimeMillis = data.totalSeconds.toLong() * 1_000
        val end = minOf(now + lifetimeMillis, data.endTimeEpochSeconds?.times(1_000) ?: Long.MAX_VALUE)
        val remaining = remainingSeconds(end, now)
        if (remaining <= 0) return
        val item = LiveSuperChatItem(data, end, remaining, now, animate)
        mutableState.value = mutableState.value.copy(items = listOf(item) + mutableState.value.items)
        if (animate) {
            pending.addLast(data.id)
            showNext()
        }
        if (ticker == null) {
            ticker = viewModelScope.launch {
                while (mutableState.value.items.isNotEmpty()) {
                    delay(1_000)
                    refreshTime()
                }
                ticker = null
            }
        }
    }

    internal fun refreshTime() {
        val now = nowMillis()
        val state = mutableState.value
        val expired = state.items.filter { it.expiresAtMillis <= now }.mapTo(mutableSetOf()) { it.data.id }
        mutableState.value = state.copy(items = state.items.map {
            it.copy(remainingSeconds = remainingSeconds(it.expiresAtMillis, now).coerceAtMost(it.data.totalSeconds))
        })
        if (expired.isNotEmpty()) remove(expired)
    }

    private fun remove(ids: Set<String>) {
        pending.removeAll { it in ids }
        val state = mutableState.value
        val selectedRemoved = state.selectedId in ids
        if (selectedRemoved) {
            displayJob?.cancel()
            displayJob = null
        }
        mutableState.value = state.copy(
            items = state.items.filterNot { it.data.id in ids },
            selectedId = state.selectedId.takeUnless { selectedRemoved },
            isExpanded = state.isExpanded && !selectedRemoved,
            isManualSelection = state.isManualSelection && !selectedRemoved,
        )
        if (selectedRemoved) showNext()
    }

    private fun showNext() {
        if (!visible || mutableState.value.selectedId != null) return
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val item = mutableState.value.items.firstOrNull { it.data.id == id && it.expiresAtMillis > nowMillis() }
                ?: continue
            mutableState.value = mutableState.value.copy(selectedId = id, isExpanded = false, isManualSelection = false)
            displayJob = viewModelScope.launch {
                val entryWait = (item.enteredAtMillis + SUPER_CHAT_ANIMATION_MILLIS - nowMillis()).coerceAtLeast(0)
                delay(entryWait)
                mutableState.value = mutableState.value.copy(isExpanded = true)
                delay(SUPER_CHAT_ANIMATION_MILLIS + 5_000L)
                mutableState.value = mutableState.value.copy(isExpanded = false)
                delay(SUPER_CHAT_ANIMATION_MILLIS.toLong())
                mutableState.value = mutableState.value.copy(selectedId = null)
                displayJob = null
                showNext()
            }
            return
        }
    }

    private fun remainingSeconds(end: Long, now: Long): Int =
        ((end - now).coerceAtLeast(0) / 1_000.0).let { kotlin.math.ceil(it).toInt() }
}
