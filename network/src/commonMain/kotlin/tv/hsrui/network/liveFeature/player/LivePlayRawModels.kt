package tv.hsrui.network.liveFeature.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RawLivePlayResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RawLivePlayData? = null,
)

@Serializable
internal data class RawLivePlayData(
    @SerialName("room_id") val roomId: Long,
    @SerialName("live_status") val liveStatus: Int,
    @SerialName("playurl_info") val playUrlInfo: RawLivePlayUrlInfo? = null,
)

@Serializable
internal data class RawLivePlayUrlInfo(
    @SerialName("playurl") val playUrl: RawLivePlayUrl? = null,
)

@Serializable
internal data class RawLivePlayUrl(
    @SerialName("stream") val streams: List<RawLivePlayProtocol> = emptyList(),
)

@Serializable
internal data class RawLivePlayProtocol(
    @SerialName("protocol_name") val protocolName: String = "",
    @SerialName("format") val formats: List<RawLivePlayFormat> = emptyList(),
)

@Serializable
internal data class RawLivePlayFormat(
    @SerialName("format_name") val formatName: String = "",
    @SerialName("master_url") val masterUrl: String? = null,
    @SerialName("codec") val codecs: List<RawLivePlayCodec> = emptyList(),
)

@Serializable
internal data class RawLivePlayCodec(
    @SerialName("codec_name") val codecName: String = "",
    @SerialName("current_qn") val currentQn: Int = 0,
    @SerialName("accept_qn") val acceptQn: List<Int> = emptyList(),
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("url_info") val urlInfo: List<RawLivePlayCdn> = emptyList(),
    @SerialName("hdr_type") val hdrType: Int = 0,
    @SerialName("drm") val isDrm: Boolean = false,
)

@Serializable
internal data class RawLivePlayCdn(
    val host: String = "",
    val extra: String = "",
)
