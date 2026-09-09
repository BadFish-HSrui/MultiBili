@file:OptIn(ExperimentalSerializationApi::class)

package tv.hsrui.network.feature.danmaku

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DanmakuSegmentResponse(
    @ProtoNumber(1) val items: List<DanmakuItem> = emptyList(),
    @ProtoNumber(2) private val stateCode: Int = 0,
) {
    val isClosed: Boolean get() = stateCode == 1
}

@Serializable
data class DanmakuItem(
    @ProtoNumber(1) val id: Long = 0,
    @ProtoNumber(2) val progressMs: Int = 0,
    @ProtoNumber(3) private val modeCode: Int = 0,
    @ProtoNumber(4) val fontSize: Int = 0,
    @ProtoNumber(5) val colorRgb: Long = 0,
    @ProtoNumber(7) val content: String = "",
    @ProtoNumber(9) val weight: Int = 0,
) {
    val mode: DanmakuMode
        get() = when (modeCode) {
            1, 2, 3 -> DanmakuMode.Scroll
            4 -> DanmakuMode.Bottom
            5 -> DanmakuMode.Top
            6 -> DanmakuMode.Reverse
            7 -> DanmakuMode.Advanced
            8 -> DanmakuMode.Code
            9 -> DanmakuMode.Bas
            else -> DanmakuMode.Unknown
        }
}

enum class DanmakuMode {
    Scroll,
    Bottom,
    Top,
    Reverse,
    Advanced,
    Code,
    Bas,
    Unknown
}
