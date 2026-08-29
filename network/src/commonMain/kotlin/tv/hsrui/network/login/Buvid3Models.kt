package tv.hsrui.network.login

import kotlinx.serialization.Serializable

@Serializable
data class RawBuvid3Response(
    val code: Int = -1,
    val data: RawBuvid3Data? = null
)

@Serializable
data class RawBuvid3Data(
    val buvid: String = ""
)
