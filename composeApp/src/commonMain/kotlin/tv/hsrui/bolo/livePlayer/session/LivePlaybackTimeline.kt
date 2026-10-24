package tv.hsrui.bolo.livePlayer.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.TimeSource

internal data class LivePlaybackTimeAnchor(val mediaMs: Long, val elapsedMs: Long, val estimated: Boolean)

data class LivePlaybackTimelineState(
    val positionMs: Long? = null,
    val latestPositionMs: Long? = null,
    val ranges: List<LiveCacheRange> = emptyList(),
    val cacheBytes: Long = 0,
    val forwardDurationMs: Long = 0,
    val playingElapsedMs: Long? = null,
    val latestElapsedMs: Long? = null,
    val estimated: Boolean = false,
    val isLive: Boolean = true,
    val canSeek: Boolean = false,
)

/** 墙钟仅在开播时间确定时采样；之后由单调时钟推进。 */
internal class LivePlaybackTimeline(
    private val wallTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val monotonicTime: () -> Long = TimeSource.Monotonic.markNow().let { mark -> { mark.elapsedNow().inWholeMilliseconds } },
) {
    private var startSeconds: Long? = null
    private var sampledElapsed: Long? = null
    private var sampledAt = 0L
    private var anchor: LivePlaybackTimeAnchor? = null
    private var cache = LiveCacheSnapshot()
    private var position: Long? = null
    private var rawLatest: Long? = null
    private var playlistTailMs = 0L
    private var live = true
    private var instantElapsed: Long? = null
    private var paused = false
    private var frozenElapsed: Long? = null
    private var ended = false
    private var endedElapsed: Long? = null
    private val mutableState = MutableStateFlow(LivePlaybackTimelineState())
    val state = mutableState.asStateFlow()

    fun setStartTime(seconds: Long?) {
        val valid = seconds?.takeIf { it > 0 && it <= wallTime() / 1000 }
        if (valid == startSeconds) return
        startSeconds = valid
        sampledElapsed = valid?.let { (wallTime() - it * 1000).coerceAtLeast(0) }
        sampledAt = monotonicTime()
        anchor = null
        update(cache)
    }

    fun reset() {
        ended = false
        endedElapsed = null
        cache = LiveCacheSnapshot()
        instantElapsed = null
        frozenElapsed = null
        position = null
        rawLatest = null
        playlistTailMs = 0
        anchor = null
        live = true
        tick()
    }

    fun finish() {
        if (ended) return
        endedElapsed = elapsed()
        ended = true
        live = false
        tick()
    }

    fun update(value: LiveCacheSnapshot, playlist: tv.hsrui.network.liveFeature.player.LiveHlsPlaylistData? = null) {
        val last = value.segments.lastOrNull()
        playlistTailMs = if (last == null) 0 else playlist?.segments
            ?.filter { it.sequence > last.sequence && it.discontinuity == last.continuity }?.sumOf { it.durationMs } ?: 0
        if (value.epoch != cache.epoch) {
            anchor = null
            rawLatest = null
        }
        cache = value
        val latest = value.segments.lastOrNull()
        val media = latest?.media
        media?.endMs?.let { rawLatest = it }
        if (media?.endMs != null && sampledElapsed != null) {
            val absolute = media.programDateTimeMs
            if (absolute != null && startSeconds != null && anchor?.estimated != false) {
                anchor = LivePlaybackTimeAnchor(media.startMs!!, (absolute - startSeconds!! * 1000).coerceAtLeast(0), false)
            } else if (anchor == null) {
                anchor = LivePlaybackTimeAnchor(media.endMs, (elapsed()!! - playlistTailMs).coerceAtLeast(0), true)
            }
        }
        tick()
    }

    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        frozenElapsed = if (value) state.value.playingElapsedMs else null
        if (value) live = false
        tick()
    }
    fun seek(positionMs: Long?, live: Boolean) {
        position = positionMs
        this.live = live && !paused && !ended
        frozenElapsed = null
        tick()
    }
    fun observe(positionMs: Long) { position = positionMs; tick() }
    fun observeInstant(playing: Boolean) {
        if (playing) instantElapsed = elapsed()
        tick()
    }
    fun tick() {
        val latest = rawLatest?.let { (it - if (ended || cache.ended) 0 else 4_000).coerceAtLeast(0) }
        val mapping = anchor
        val mapped = if (mapping != null && position != null) (mapping.elapsedMs + position!! - mapping.mediaMs).coerceAtLeast(0) else null
        val range = cache.ranges.firstOrNull { position != null && position!! >= it.startMs && position!! < it.endMs }
        val playingElapsed = if (paused) frozenElapsed ?: mapped ?: instantElapsed else mapped ?: instantElapsed
        if (paused) frozenElapsed = playingElapsed
        mutableState.value = LivePlaybackTimelineState(
            position,
            latest,
            cache.ranges,
            cache.cachedBytes,
            if (position != null && range != null) range.endMs - position!! else 0,
            playingElapsed,
            if (latest != null && mapping != null) (mapping.elapsedMs + latest - mapping.mediaMs).coerceAtLeast(0)
                else elapsed()?.let { (it - if (ended) 0 else 4_000).coerceAtLeast(0) },
            mapping?.estimated == true,
            live,
            cache.ranges.isNotEmpty(),
        )
    }
    private fun elapsed(): Long? = if (ended) endedElapsed else sampledElapsed?.plus((monotonicTime() - sampledAt).coerceAtLeast(0))
}
