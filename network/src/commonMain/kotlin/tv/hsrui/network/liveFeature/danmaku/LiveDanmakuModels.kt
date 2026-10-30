package tv.hsrui.network.liveFeature.danmaku

data class LiveDanmakuSendConfigData(
    val maxLength: Int,
    val bubbleColorArgb: Long? = null,
    val bubbleId: Long? = null,
)

data class LiveDanmakuData(
    val id: String = "",
    val sentAtEpochMillis: Long? = null,
    val username: String,
    val content: String,
    val replyUsername: String = "",
    val emoticon: LiveDanmakuEmoticonData? = null,
    val mode: LiveDanmakuMode? = null,
    val fontSize: Float = 25f,
    val colorRgb: Long = 0xFFFFFF,
    val inlineEmoticons: Map<String, LiveDanmakuEmoticonData> = emptyMap(),
    val user: LiveDanmakuUserData = LiveDanmakuUserData(),
    val spaceUrl: String? = null,
    val bubbleColorArgb: Long? = null,
    val bubbleId: Long? = null,
)

data class LiveDanmakuUserData(
    val uid: Long? = null,
    val isRoomAdmin: Boolean? = null,
    val isMystery: Boolean? = null,
    val fansMedal: LiveDanmakuFansMedalData? = null,
    val guardLevel: LiveDanmakuGuardLevel? = null,
    val wealthLevel: Int? = null,
    val userLevel: Int? = null,
)

data class LiveDanmakuFansMedalData(
    val name: String,
    val level: Int,
    val anchorUid: Long? = null,
    val isLighted: Boolean = false,
    val startColorArgb: Long? = null,
    val endColorArgb: Long? = null,
    val borderColorArgb: Long? = null,
    val textColorArgb: Long? = null,
    val guardLevel: LiveDanmakuGuardLevel? = null,
)

enum class LiveDanmakuGuardLevel {
    None,
    Governor,
    Admiral,
    Captain,
}

data class LiveDanmakuMedalResourcesData(
    val guardIcons: Map<LiveDanmakuGuardLevel, String> = emptyMap(),
    val thousandGuardIcons: Map<LiveDanmakuGuardLevel, String> = emptyMap(),
    val thousandGuardAnchorUids: Set<Long> = emptySet(),
) {
    fun iconUrl(medal: LiveDanmakuFansMedalData): String? {
        if (!medal.isLighted || medal.guardLevel == null || medal.guardLevel == LiveDanmakuGuardLevel.None) return null
        return if (medal.anchorUid in thousandGuardAnchorUids) {
            thousandGuardIcons[medal.guardLevel] ?: guardIcons[medal.guardLevel]
        } else guardIcons[medal.guardLevel]
    }
}

data class LiveDanmakuBubbleData(
    val url: String,
    val width: Int,
    val height: Int,
    val isFleet: Boolean = false,
    val firstLineUrl: String? = null,
)

data class LiveDanmakuEmoticonData(
    val url: String,
    val text: String,
    val width: Int = 1,
    val height: Int = 1,
)

enum class LiveDanmakuMode {
    Scroll,
    Top,
    Bottom,
}
