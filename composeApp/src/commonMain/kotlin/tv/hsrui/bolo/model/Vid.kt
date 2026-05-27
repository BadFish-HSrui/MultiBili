package tv.hsrui.bolo.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
sealed interface Vid{
    val key: String

    @Serializable
    @JvmInline
    value class AVid(val value: Long) : Vid { override val key get() = value.toString() }
    @Serializable
    @JvmInline
    value class BVid(val value: String) : Vid { override val key get() = value }
}