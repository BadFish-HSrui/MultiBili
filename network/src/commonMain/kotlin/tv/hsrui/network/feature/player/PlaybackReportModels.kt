package tv.hsrui.network.feature.player

data class PlaybackReportTarget(
    val avid: Long,
    val cid: Long,
    val episodeId: Long? = null,
    val seasonId: Long = 0L,
    val seasonType: Int = 0,
) {
    val isMedia: Boolean get() = episodeId != null || seasonId != 0L || seasonType != 0
    val isValid: Boolean
        get() = avid > 0L && cid > 0L &&
            (!isMedia || ((episodeId ?: 0L) > 0L && seasonId > 0L && seasonType > 0))
}
