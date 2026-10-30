package tv.hsrui.bolo.livePlayer.danmaku

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.boloSetting.setting.live.LiveDanmakuSettings
import tv.hsrui.network.liveFeature.danmaku.parseLiveDanmakuMessage
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.message.LiveMessageManager
import tv.hsrui.network.login.storage.LoginStorage

class LiveDanmakuController(
    scope: CoroutineScope,
    private val manager: LiveMessageManager,
    val settings: LiveDanmakuSettings,
    private val roomId: () -> Long?,
    accountChanges: Flow<Long> = getKoin().get<LoginStorage>().currentUserMidFlow,
    sentMessages: Flow<LiveDanmakuData> = emptyFlow(),
) {
    internal val engine = LiveDanmakuEngine()
    private var pageActive by mutableStateOf(false)
    private var attached by mutableStateOf(false)
    private var closed = false
    internal val isPageActive: Boolean get() = pageActive
    val isActive: Boolean get() = !closed && pageActive && attached && settings.enabled
    private val seenIds = LinkedHashSet<String>()
    private var generation = -1L
    private val messagesJob = scope.launch {
        manager.messages.collect { message ->
            if (!isActive || message.roomId != roomId() || !manager.isCurrent(message.roomId, message.generation)) return@collect
            if (generation != message.generation) {
                clear()
                generation = message.generation
            }
            parseLiveDanmakuMessage(message)?.let(::append)
        }
    }
    private val sentMessagesJob = scope.launch {
        sentMessages.collect { data ->
            if (isActive) append(data)
        }
    }
    private val settingsJob = scope.launch {
        snapshotFlow { listOf(isActive, settings.scrollEnabled, settings.topEnabled, settings.bottomEnabled) }
            .collect { if (!isActive) clear() else engine.filter(settings::allows) }
    }
    private val connectionJob = scope.launch {
        manager.connectionState.collect {
            if (generation != it.generation) {
                clear()
                generation = it.generation
            }
        }
    }

    private val accountJob = scope.launch {
        accountChanges.distinctUntilChanged().collect { clear() }
    }

    private fun append(data: LiveDanmakuData) {
        if (data.id.isNotEmpty() && !seenIds.add(data.id)) return
        if (seenIds.size > 1_000) seenIds.remove(seenIds.first())
        if (settings.allows(data.mode)) engine.enqueue(data)
    }

    fun setActive(value: Boolean) {
        pageActive = value
        if (!value) clear()
    }

    internal fun attach(value: Boolean) {
        attached = value
        if (!value) clear()
    }

    private fun clear() {
        engine.clear()
        seenIds.clear()
    }

    fun close() {
        closed = true
        messagesJob.cancel()
        sentMessagesJob.cancel()
        settingsJob.cancel()
        connectionJob.cancel()
        accountJob.cancel()
        clear()
    }
}
