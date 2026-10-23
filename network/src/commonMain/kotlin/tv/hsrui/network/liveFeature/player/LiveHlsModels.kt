package tv.hsrui.network.liveFeature.player

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlin.math.roundToLong
import kotlin.time.Instant

data class LiveHlsResourceData(val url: String, val range: LongRange? = null)

data class LiveHlsSegmentData(
    val sequence: Long,
    val discontinuity: Long,
    val durationMs: Long,
    val resource: LiveHlsResourceData,
    val initialization: LiveHlsResourceData?,
    val programDateTimeMs: Long?,
    val isGap: Boolean,
)

data class LiveHlsPlaylistData(
    val url: String,
    val targetDurationMs: Long,
    val segments: List<LiveHlsSegmentData>,
    val ended: Boolean,
)

internal fun parseLiveHlsPlaylist(content: String, url: String): LiveHlsPlaylistData {
    val lines = content.removePrefix("\uFEFF").lineSequence().map(String::trim).filter(String::isNotEmpty).iterator()
    check(lines.hasNext() && lines.next() == "#EXTM3U") { "直播媒体清单格式无效" }
    var target = 0L
    var sequence = 0L
    var discontinuity = 0L
    var duration: Long? = null
    var byteRange: String? = null
    var initialization: LiveHlsResourceData? = null
    var previous: LiveHlsResourceData? = null
    var date: Long? = null
    var gap = false
    var ended = false
    val segments = mutableListOf<LiveHlsSegmentData>()
    fun positiveDuration(value: String): Long {
        val seconds = value.toDoubleOrNull()
        check(seconds != null && seconds.isFinite() && seconds > 0 && seconds < Long.MAX_VALUE / 1000.0) {
            "直播分片时长无效"
        }
        return (seconds * 1000).roundToLong().also { check(it > 0) { "直播分片时长无效" } }
    }
    fun attributes(line: String): Map<String, String> =
        Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""").findAll(line.substringAfter(':')).associate {
            it.groupValues[1] to it.groupValues[2].removeSurrounding("\"")
        }
    fun resource(uri: String, range: String?, preceding: LiveHlsResourceData?): LiveHlsResourceData {
        val resolved = URLBuilder(url).apply { parameters.clear(); fragment = "" }.takeFrom(uri).buildString()
        val parsed = Url(resolved)
        check(parsed.protocol == URLProtocol.HTTP || parsed.protocol == URLProtocol.HTTPS) { "直播分片地址无效" }
        val bytes = range?.let {
            val parts = it.split('@')
            check(parts.size in 1..2) { "直播分片范围无效" }
            val length = parts[0].toLongOrNull()
            val start = if (parts.size == 2) parts[1].toLongOrNull() else
                preceding?.takeIf { item -> item.url == resolved }?.range?.last?.plus(1)
            check(length != null && length > 0 && start != null && start >= 0 && start < Long.MAX_VALUE - length) {
                "直播分片范围无效"
            }
            start until start + length
        }
        return LiveHlsResourceData(resolved, bytes)
    }
    for (line in lines) {
        when {
            line.startsWith("#EXT-X-TARGETDURATION:") -> target = positiveDuration(line.substringAfter(':'))
            line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                check(segments.isEmpty()) { "直播分片序号位置无效" }
                sequence = checkNotNull(line.substringAfter(':').toLongOrNull()?.takeIf { it >= 0 }) { "直播分片序号无效" }
            }
            line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") -> {
                check(segments.isEmpty()) { "直播连续区间位置无效" }
                discontinuity = checkNotNull(line.substringAfter(':').toLongOrNull()?.takeIf { it >= 0 }) { "直播连续区间无效" }
            }
            line == "#EXT-X-DISCONTINUITY" -> {
                check(discontinuity < Long.MAX_VALUE)
                discontinuity++
                date = null
            }
            line.startsWith("#EXTINF:") -> {
                check(duration == null) { "直播分片地址缺失" }
                duration = positiveDuration(line.substringAfter(':').substringBefore(','))
            }
            line.startsWith("#EXT-X-BYTERANGE:") -> byteRange = line.substringAfter(':')
            line.startsWith("#EXT-X-MAP:") -> {
                val values = attributes(line)
                initialization = resource(checkNotNull(values["URI"]) { "直播初始化地址缺失" }, values["BYTERANGE"], initialization)
            }
            line.startsWith("#EXT-X-KEY:") -> check(attributes(line)["METHOD"] == "NONE") { "不支持加密的直播媒体清单" }
            line.startsWith("#EXT-X-PROGRAM-DATE-TIME:") -> date =
                runCatching { Instant.parse(line.substringAfter(':')).toEpochMilliseconds() }.getOrNull()
            line == "#EXT-X-GAP" -> gap = true
            line == "#EXT-X-ENDLIST" -> ended = true
            line.startsWith("#EXT-X-STREAM-INF:") -> error("需要直播媒体清单，收到多画质清单")
            !line.startsWith('#') -> {
                val length = checkNotNull(duration) { "直播分片时长缺失" }
                check(sequence < Long.MAX_VALUE)
                val media = resource(line, byteRange, previous)
                segments += LiveHlsSegmentData(sequence++, discontinuity, length, media, initialization, date, gap)
                date = date?.takeIf { it <= Long.MAX_VALUE - length }?.plus(length)
                previous = media
                duration = null
                byteRange = null
                gap = false
            }
        }
    }
    check(target > 0 && duration == null) { "直播媒体清单不完整" }
    return LiveHlsPlaylistData(url, target, segments, ended)
}
