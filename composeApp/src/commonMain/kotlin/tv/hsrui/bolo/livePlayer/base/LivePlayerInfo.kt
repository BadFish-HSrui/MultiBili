package tv.hsrui.bolo.livePlayer.base

import tv.hsrui.bolo.livePlayer.session.LivePlaybackTimelineState
import tv.hsrui.bolo.player.base.BoloPlayerInfo

/** 码率为 bit/s，下载速度为 B/s，容量为字节，时长为 ms；null 表示未知或不适用。 */
data class LivePlayerInfo(
    val currentQn: Int? = null,
    val protocolName: String? = null,
    val formatName: String? = null,
    val media: BoloPlayerInfo = BoloPlayerInfo(),
    val downloadBytesPerSecond: Long? = null,
    val receivedBytes: Long? = null,
    val cachedBytes: Long? = null,
    val cachedDurationMs: Long? = null,
    val forwardDurationMs: Long? = null,
    val liveOffsetMs: Long? = null,
) {
    val totalPlaybackBitrateBps: Long?
        get() {
            val video = media.video.playbackBitrateBps?.takeIf { it >= 0 } ?: return null
            val audio = media.audio?.playbackBitrateBps?.takeIf { it >= 0 } ?: return null
            return if (video <= Long.MAX_VALUE - audio) video + audio else null
        }
}

internal fun LivePlayerInfo.withTransferStatistics(
    receivedBytes: Long,
    cachedBytes: Long,
    downloadBytesPerSecond: Long?,
    timeline: LivePlaybackTimelineState?,
): LivePlayerInfo = copy(
    receivedBytes = receivedBytes,
    cachedBytes = cachedBytes,
    downloadBytesPerSecond = downloadBytesPerSecond,
    cachedDurationMs = timeline?.ranges?.sumOf { (it.endMs - it.startMs).coerceAtLeast(0) },
    forwardDurationMs = timeline?.forwardDurationMs,
    liveOffsetMs = timeline?.latestPositionMs?.let { latest ->
        timeline.positionMs?.let { (latest - it).coerceAtLeast(0) }
    },
)
