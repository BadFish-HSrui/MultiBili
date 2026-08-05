package tv.hsrui.network.model

import kotlinx.serialization.Serializable

@Serializable
data class Owner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)