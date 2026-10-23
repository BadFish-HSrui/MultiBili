package tv.hsrui.network.liveFeature.player

data class LiveStreamResponseData(
    val statusCode: Int,
    val url: String,
    val start: Long,
    val endExclusive: Long?,
)
