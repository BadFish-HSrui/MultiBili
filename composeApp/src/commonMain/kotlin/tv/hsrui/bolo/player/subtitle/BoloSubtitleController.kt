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
import tv.hsrui.network.feature.subtitle.fetchSubtitleList

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
    private var listJob: Job? = null
    private var contentJob: Job? = null
    private var positionMs = 0L
    private val cache = mutableMapOf<SubtitleItem, List<SubtitleCue>>()

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

    fun loadSubtitleList(avid: Long, cid: Long) {
        if (media == (avid to cid)) return
        clear()
        if (avid <= 0L || cid <= 0L) return
        media = avid to cid
        val requestGeneration = generation
        listJob = scope.launch {
            try {
                val response = fetchSubtitleList(avid, cid)
                if (generation != requestGeneration) return@launch
                check(response.isSuccess) { response.message }
                _state.value = BoloSubtitleState(subtitles = response.subtitles.filter { it.url.isNotBlank() })
                if (alwaysOn) {
                    automaticSubtitle?.let { loadSubtitleContent(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration) println("字幕列表加载失败: ${e.message}")
            }
        }
    }

    fun loadSubtitleContent(subtitle: SubtitleItem?) {
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
                val response = fetchSubtitleContent(subtitle.url)
                if (generation != requestGeneration || selectionGeneration != requestSelection) return@launch
                cache[subtitle] = response.cues
                engine.load(response.cues)
                synchronize(positionMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == requestGeneration && selectionGeneration == requestSelection) {
                    _state.value = _state.value.copy(selected = null, text = "")
                    println("字幕内容加载失败: ${e.message}")
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
        listJob?.cancel()
        contentJob?.cancel()
        listJob = null
        contentJob = null
        media = null
        positionMs = 0L
        cache.clear()
        engine.load(emptyList())
        _state.value = BoloSubtitleState()
    }
}
