package tv.hsrui.network.liveFeature.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RawLiveMessageResponse(
    val code: Int = -1,
    val data: RawLiveMessageData? = null,
)

@Serializable
internal data class RawLiveMessageData(
    val token: String = "",
    @SerialName("host_list") val hosts: List<RawLiveMessageHost> = emptyList(),
)

@Serializable
internal data class RawLiveMessageHost(
    val host: String = "",
    @SerialName("wss_port") val port: Int = 443,
)
