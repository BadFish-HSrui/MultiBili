package tv.hsrui.bolo.player.subtitle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.subtitle.SubtitleCue
import tv.hsrui.network.feature.subtitle.SubtitleItem
import tv.hsrui.network.feature.subtitle.fetchSubtitleContent

data class BoloSubtitleState(
    val subtitles: List<SubtitleItem> = emptyList(),
    val selected: SubtitleItem? = null,
    val text: String = "",
)

class BoloSubtitleController(private val scope: CoroutineScope) {
    private val engine = BoloSubtitleEngine()
    private val _state = MutableStateFlow(BoloSubtitleState())
    val state = _state.asStateFlow()
    private var media: Pair<Long, Long>? = null
    private var generation = 0L
    private var selectionGeneration = 0L
    private var contentJob: Job? = null
    private var positionMs = 0L
    private val cache = mutableMapOf<SubtitleItem, List<SubtitleCue>>()
    private var requestSelection = 0L
    private var requestAlwaysOn = false
    private var requestSmartEnabled = true
    private var requestChineseOnly = false
    private var requestExcludeAi = false
    private var sessionIsCurrent: () -> Boolean = { true }

    var smartEnabled: Boolean = true
    var autoChineseOnly: Boolean = false
    var autoExcludeAi: Boolean = false
    private val automaticSubtitle: SubtitleItem?
        get() = _state.value.subtitles.firstOrNull {
            (!autoChineseOnly || it.isChinese) && (!autoExcludeAi || !it.isAiGenerated)
        }

    var alwaysOn: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value && _state.value.selected == null) {
                automaticSubtitle?.let { loadSubtitleContent(it) }
            }
        }

    fun beginSubtitleLoad(avid: Long, cid: Long, sessionIsCurrent: () -> Boolean = { true }): Long {
        clear()
        if (avid <= 0L || cid <= 0L) return generation
        media = avid to cid
        this.sessionIsCurrent = sessionIsCurrent
        requestSelection = selectionGeneration
        requestAlwaysOn = alwaysOn
        requestSmartEnabled = smartEnabled && !alwaysOn
        requestChineseOnly = autoChineseOnly
        requestExcludeAi = autoExcludeAi
        return generation
    }

    fun loadSubtitleList(avid: Long, cid: Long, subtitles: List<SubtitleItem>, requestGeneration: Long) {
        if (generation != requestGeneration || media != (avid to cid) || !sessionIsCurrent()) return
        val playable = subtitles.filter { it.isPlayable }
        _state.value = _state.value.copy(subtitles = playable)
        if (selectionGeneration != requestSelection) return
        val selected = when {
            requestAlwaysOn -> playable.firstOrNull {
                (!requestChineseOnly || it.isChinese) && (!requestExcludeAi || !it.isAiGenerated)
            }
            requestSmartEnabled -> {
                val ass = playable.filter { it.isAss }
                val candidates = ass.ifEmpty { playable }
                val main = candidates.firstOrNull { it.isMain } ?: ass.firstOrNull()
                if (main?.isAiGenerated == true && requestExcludeAi) {
                    playable.firstOrNull { !it.isAiGenerated && it.isChinese }
                } else main
            }
            else -> null
        }
        selected?.let { loadSubtitleContent(it) }
    }

    fun loadSubtitleContent(subtitle: SubtitleItem?) {
        if (subtitle != null && !sessionIsCurrent()) return
        if (subtitle != null && subtitle !in _state.value.subtitles) return
        contentJob?.cancel()
        selectionGeneration++
        val requestSelection = selectionGeneration
        val requestGeneration = generation
        engine.load(emptyList())
        _state.value = _state.value.copy(selected = subtitle, text = "")
        if (subtitle == null) return
        val cached = cache[subtitle]
        if (cached != null) {
            engine.load(cached)
            synchronize(positionMs)
            return
        }
        contentJob = scope.launch {
            try {
                val response = fetchSubtitleContent(subtitle.url, subtitle.isAss)
                if (generation != requestGeneration || selectionGeneration != requestSelection || !sessionIsCurrent()) return@launch
                cache[subtitle] = response.cues
                engine.load(response.cues)
                synchronize(positionMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration && selectionGeneration == requestSelection && sessionIsCurrent()) {
                    _state.value = _state.value.copy(selected = null, text = "")
                    println("字幕内容加载失败")
                }
            }
        }
    }

    fun synchronize(positionMs: Long) {
        this.positionMs = positionMs
        _state.value = _state.value.copy(text = engine.textAt(positionMs))
    }

    fun clear() {
        generation++
        selectionGeneration++
        contentJob?.cancel()
        contentJob = null
        media = null
        sessionIsCurrent = { true }
        positionMs = 0L
        cache.clear()
        engine.load(emptyList())
        _state.value = BoloSubtitleState()
    }
}
