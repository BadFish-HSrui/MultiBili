package tv.hsrui.bolo.player.base

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/** 普通 native 调用由串行 dispatcher 执行；GL 只在平台渲染上下文执行。 */
internal expect class BoloMpvBackend() {
    val retainsPausedResources: Boolean
    fun retainedPosition(generation: Long, positionMs: Long): Long?
    suspend fun bind(output: Any)
    suspend fun detachOutput(output: Any)
    suspend fun unbind()
    suspend fun awaitOutput()
    fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String): Int
    fun videoEnabled(enabled: Boolean): Int
    fun pause(paused: Boolean): Int
    fun speed(speed: Double): Int
    fun volume(volume: Double): Int
    fun loudness(gainDb: Double, dynamicEnabled: Boolean, targetLufs: Double = -14.0, rangeLu: Double = 11.0, truePeakDbtp: Double = -2.0): Int
    fun mergeAudioChannels(enabled: Boolean): Int
    fun seek(seconds: Double, request: Long): Int
    fun poll(): BoloMpvEvent?
    fun info(includeDiagnostics: Boolean): BoloMpvInfoSnapshot?
    fun stop(): Int
    fun destroy()
    suspend fun setAudioActive(active: Boolean): Boolean
}

internal class BoloMpvEvent(
    val type: Int,
    val generation: Long,
    val request: Long,
    val error: Int,
    val value: Double,
) {
    var observation: BoloPlaybackObservation? = null
    companion object {
        const val Loaded = 1
        const val Position = 2
        const val Duration = 3
        const val Seekable = 4
        const val Paused = 5
        const val Buffering = 6
        const val Seeking = 7
        const val Eof = 8
        const val Error = 9
        const val SeekReply = 10
        const val Restart = 11
        const val Overflow = 12
    }
}

/** 原生采样时的累计值；交付延迟不改变耗时，位置只由控制器确认后附加。 */
internal class BoloPlaybackObservation(
    val generation: Long,
    val continuity: Long,
    val playedTime: Duration,
    val positionAdvanceMs: Double,
    val playing: Boolean,
    val viewedAtMs: Long,
    val positionMs: Long? = null,
    val durationMs: Long = 0L,
    val startedPlayCount: Long = 0L,
    val endedPlayCount: Long = 0L,
    val dailyPlayedTime: Map<String, Duration> = emptyMap(),
    val dailyStartedPlayCount: Map<String, Long> = emptyMap(),
    val firstStartedLocalDate: String? = null,
) {
    fun withPosition(position: Long) = BoloPlaybackObservation(
        generation, continuity, playedTime, positionAdvanceMs, playing, viewedAtMs, position, durationMs,
        startedPlayCount, endedPlayCount, dailyPlayedTime, dailyStartedPlayCount, firstStartedLocalDate,
    )
}

/** 仅由原生串行 dispatcher 修改；Main 只读取不可变快照，不外推排队事件的时间。 */
internal class BoloPlaybackClock(
    private val time: () -> Duration = TimeSource.Monotonic.markNow().let { origin -> { origin.elapsedNow() } },
    private val wallTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val currentTimeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    val latest = MutableStateFlow<BoloPlaybackObservation?>(null)
    private var generation = -1L
    private var continuity = 0L
    private var lastTime = time()
    private var lastWallTime = wallTime()
    private var timeZone = currentTimeZone()
    private var timeZoneCheckedAt = lastTime
    private var playedTime = Duration.ZERO
    private var advanceMs = 0.0
    private var speed = 1.0
    private var loaded = false
    private var paused = true
    private var buffering = false
    private var seeking = false
    private var ended = false
    private var startedPlayCount = 0L
    private var endedPlayCount = 0L
    private var dailyPlayedTime = emptyMap<String, Duration>()
    private var dailyStartedPlayCount = emptyMap<String, Long>()
    private var firstStartedLocalDate: String? = null
    private var durationMs = 0L
    private var viewedAtMs = wallTime()
    private val playing get() = loaded && !paused && !buffering && !seeking && !ended

    fun begin(value: Long, rate: Double) {
        generation = value
        continuity++
        lastTime = time()
        lastWallTime = wallTime()
        playedTime = Duration.ZERO
        advanceMs = 0.0
        speed = rate
        loaded = false
        paused = true
        buffering = false
        seeking = false
        ended = false
        startedPlayCount = 0L
        endedPlayCount = 0L
        dailyPlayedTime = emptyMap()
        dailyStartedPlayCount = emptyMap()
        firstStartedLocalDate = null
        durationMs = 0L
        sample(refreshTimeZone = true)
    }

    fun setSpeed(value: Double) {
        sample()
        speed = value
    }

    fun setPaused(value: Boolean) {
        sample()
        val resuming = paused && !value
        paused = value
        sample(refreshTimeZone = resuming)
    }

    fun beginSeek() {
        sample()
        continuity++
        seeking = true
        sample()
    }

    fun observe(event: BoloMpvEvent): BoloPlaybackObservation? {
        if (event.generation != generation) return null
        sample()
        val wasPlaying = playing
        when (event.type) {
            BoloMpvEvent.Loaded -> loaded = true
            BoloMpvEvent.Paused -> paused = event.value != 0.0
            BoloMpvEvent.Buffering -> buffering = event.value != 0.0
            BoloMpvEvent.Seeking -> {
                if (event.value != 0.0 && !seeking) continuity++
                seeking = event.value != 0.0
            }
            BoloMpvEvent.Eof -> {
                ended = event.value != 0.0
                if (ended && loaded && !seeking) endedPlayCount = startedPlayCount
            }
            BoloMpvEvent.Error, BoloMpvEvent.Overflow -> loaded = false
            BoloMpvEvent.Duration -> if (event.value.isFinite() && event.value > 0.0) {
                durationMs = (event.value * 1_000).toLong()
            }
        }
        return sample(refreshTimeZone = !wasPlaying && playing).also { event.observation = it }
    }

    fun sample(refreshTimeZone: Boolean = false): BoloPlaybackObservation {
        val now = time()
        val nowWallTime = wallTime()
        val elapsed = (now - lastTime).coerceAtLeast(Duration.ZERO)
        var zoneChanged = false
        if (refreshTimeZone || now - timeZoneCheckedAt >= 1.seconds) {
            val updated = currentTimeZone()
            zoneChanged = updated != timeZone
            timeZone = updated
            timeZoneCheckedAt = now
        }
        if (playing && elapsed > Duration.ZERO) {
            // 首次实际播放或 EOF 后再次实际播放才增加；暂停、缓冲和 seek 不重新计次。
            val starts = startedPlayCount == endedPlayCount
            if (starts) startedPlayCount++
            val clockChanged = nowWallTime < lastWallTime ||
                ((nowWallTime - lastWallTime).milliseconds - elapsed).absoluteValue > 1.seconds
            addDailyPlayback(elapsed, nowWallTime, zoneChanged || clockChanged, starts)
            playedTime += elapsed
            advanceMs += elapsed.inWholeNanoseconds / 1_000_000.0 * speed
            viewedAtMs = nowWallTime
        }
        lastTime = now
        lastWallTime = nowWallTime
        return BoloPlaybackObservation(generation, continuity, playedTime, advanceMs, playing, viewedAtMs,
            durationMs = durationMs, startedPlayCount = startedPlayCount,
            endedPlayCount = endedPlayCount, dailyPlayedTime = dailyPlayedTime,
            dailyStartedPlayCount = dailyStartedPlayCount,
            firstStartedLocalDate = firstStartedLocalDate).also { latest.value = it }
    }

    private fun addDailyPlayback(elapsed: Duration, wallTimeMs: Long, clockChanged: Boolean, starts: Boolean) {
        // 从已观察的起点分配单调耗时，避免结束时间的毫秒截断把午夜首播推回前一天。
        var cursor = Instant.fromEpochMilliseconds(if (clockChanged) wallTimeMs else lastWallTime)
        val end = cursor + elapsed
        val startDate = cursor.toLocalDateTime(timeZone).date.toString()
        if (starts) {
            if (firstStartedLocalDate == null) firstStartedLocalDate = startDate
            dailyStartedPlayCount = dailyStartedPlayCount +
                (startDate to ((dailyStartedPlayCount[startDate] ?: 0L) + 1L))
        }
        if (clockChanged) {
            // 调整设备时间只改变日期归属，不能把时钟跳过的间隔算成播放耗时。
            dailyPlayedTime = dailyPlayedTime + (startDate to ((dailyPlayedTime[startDate] ?: Duration.ZERO) + elapsed))
            return
        }
        while (cursor < end) {
            val date = cursor.toLocalDateTime(timeZone).date
            val boundary = (date + DatePeriod(days = 1)).atStartOfDayIn(timeZone)
            val next = minOf(end, boundary)
            val key = date.toString()
            dailyPlayedTime = dailyPlayedTime + (key to ((dailyPlayedTime[key] ?: Duration.ZERO) + (next - cursor)))
            cursor = next
        }
    }
}
