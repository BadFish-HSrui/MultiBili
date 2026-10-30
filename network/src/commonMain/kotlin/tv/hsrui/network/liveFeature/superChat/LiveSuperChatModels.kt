package tv.hsrui.network.liveFeature.superChat

import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuUserData

data class LiveSuperChatData(
    val id: String,
    val content: String,
    val amountYuan: String,
    val username: String,
    val avatarUrl: String,
    val user: LiveDanmakuUserData,
    val totalSeconds: Int,
    val endTimeEpochSeconds: Long? = null,
    val backgroundColorArgb: Long? = null,
    val bottomColorArgb: Long? = null,
    val messageColorArgb: Long? = null,
)
