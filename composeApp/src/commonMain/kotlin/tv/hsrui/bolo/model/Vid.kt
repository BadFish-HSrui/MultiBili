package tv.hsrui.bolo.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface Vid{
    val key: String

    @Serializable
    value class AVid(val value: Long) : Vid { override val key get() = value.toString() }
    @Serializable
    value class BVid(val value: String) : Vid { override val key get() = value }
}