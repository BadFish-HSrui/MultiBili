package tv.hsrui.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Cookie(
    @SerialName("DedeUserID__ckMd5") val dedeUserIDCkMd5: String = "",
    @SerialName("DedeUserID") val dedeUserID: Long = 0,
    @SerialName("SESSDATA") val sessData: String = "",
    @SerialName("bili_jct") val biliJct: String = "",
    @SerialName("b_nut") val bNut: Long = 0,
    @SerialName("sid") val sid: String = "",

    @SerialName("buvid_fp") val buvidFp: String = "",
    @SerialName("buvid3") val buvid3: String = "",
    @SerialName("buvid4") val buvid4: String = "",
)