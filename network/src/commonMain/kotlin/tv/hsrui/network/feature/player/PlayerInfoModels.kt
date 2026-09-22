package tv.hsrui.network.feature.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class PlayerInfoResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: PlayerInfoData? = null,
) {
    @Transient private var requestMedia: Pair<Long, Long>? = null
    @Transient private var requestAccountSession: String? = null

    val isSuccess: Boolean get() = code == 0 && data != null
    val needLoginSubtitle: Boolean? get() = data?.needLoginSubtitle
    val asrLanguage: String? get() = data?.asrLanguage.takeIf { isSuccess }
    val ocrLanguage: String? get() = data?.ocrLanguage.takeIf { isSuccess }
    val lastPlayCid: Long get() = if (isSuccess) data?.lastPlayCid ?: 0L else 0L
    val lastPlayPositionMs: Long get() = if (isSuccess) data?.lastPlayPositionMs ?: 0L else 0L
    val chapters: List<PlayerChapterData> get() = if (isSuccess) data?.chapters.orEmpty() else emptyList()
    val danmakuUserHash: String get() = if (isSuccess) data?.danmakuUserHash.orEmpty() else ""
    val canSendDanmaku: Boolean get() = isSuccess && data?.canSendDanmaku == true
    val danmakuMaxLength: Int get() = if (isSuccess) data?.danmakuMaxLength ?: 0 else 0
    val danmakuCooldownMs: Long get() = if (isSuccess) data?.danmakuCooldownMs ?: 0L else 0L

    internal fun bindRequest(avid: Long, cid: Long, accountSession: String): PlayerInfoResponse = apply {
        requestMedia = avid to cid
        requestAccountSession = accountSession
    }

    fun matchesRequest(avid: Long, cid: Long, accountSession: String): Boolean =
        requestMedia == (avid to cid) && requestAccountSession == accountSession

    fun resumePositionMs(cid: Long, durationMs: Long): Long = lastPlayPositionMs.takeIf {
        lastPlayCid == cid && it > 0L && (durationMs <= 0L || it < durationMs)
    } ?: 0L
}

@Serializable
data class PlayerInfoData(
    @SerialName("need_login_subtitle") val needLoginSubtitle: Boolean? = null,
    @SerialName("asr_language") val asrLanguage: String? = null,
    @SerialName("ocr_language") val ocrLanguage: String? = null,
    @SerialName("last_play_cid") val lastPlayCid: Long? = null,
    @SerialName("last_play_time") val lastPlayPositionMs: Long? = null,
    @SerialName("view_points") private val viewPoints: JsonElement? = null,
    @SerialName("login_mid") private val loginMid: Long = 0L,
    @SerialName("login_mid_hash") val danmakuUserHash: String = "",
    @SerialName("permission") private val permission: String? = null,
    @SerialName("block_time") private val blockTime: Long? = null,
    @SerialName("level_info") private val levelInfo: JsonObject? = null,
    @SerialName("is_ugc_pay_preview") private val isPaidPreview: Boolean = false,
) {
    private val danmakuPermissions: Set<String> get() = permission?.split(',')?.toSet().orEmpty()
    private val hasLimitedDanmaku: Boolean get() = "9999" in danmakuPermissions
    private val hasShortDanmaku: Boolean get() = hasLimitedDanmaku || "5000" in danmakuPermissions
    val canSendDanmaku: Boolean
        get() = loginMid > 0L && permission != null && blockTime == 0L && !hasLimitedDanmaku &&
            ((levelInfo?.get("current_level") as? JsonPrimitive)?.intOrNull ?: 0) > 0 && !isPaidPreview
    val danmakuMaxLength: Int get() = if (hasShortDanmaku) 20 else 100
    val danmakuCooldownMs: Long
        get() = when {
            hasShortDanmaku -> 10_000L
            danmakuPermissions.any { it in setOf("20000", "32000", "31300", "30000", "25000") } -> 1_000L
            else -> 5_000L
        }

    // 可选章节逐项容错，不让异常条目影响字幕和历史进度。
    val chapters: List<PlayerChapterData>
        get() = (viewPoints as? JsonArray).orEmpty().mapNotNull { element ->
            val point = element as? JsonObject ?: return@mapNotNull null
            if ((point["type"] as? JsonPrimitive)?.intOrNull != 2) return@mapNotNull null
            val title = (point["content"] as? JsonPrimitive)?.takeIf { it.isString }
                ?.content?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val start = (point["from"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val end = (point["to"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val limit = Long.MAX_VALUE.toDouble() / 1000.0
            if (!start.isFinite() || !end.isFinite() || start < 0.0 || end <= start || end >= limit) {
                return@mapNotNull null
            }
            val startMs = (start * 1000.0).toLong()
            val endMs = (end * 1000.0).toLong()
            if (endMs <= startMs) return@mapNotNull null
            PlayerChapterData(title, startMs, endMs)
        }.sortedBy { it.startMs }.distinctBy { it.startMs }
}

data class PlayerChapterData(
    val title: String,
    val startMs: Long,
    val endMs: Long,
) {
    fun clippedTo(durationMs: Long): PlayerChapterData? =
        if (durationMs <= 0L || startMs >= durationMs) null else copy(endMs = minOf(endMs, durationMs))

    fun contains(positionMs: Long, durationMs: Long): Boolean =
        positionMs >= startMs && (positionMs < endMs || (positionMs == durationMs && endMs == durationMs))
}
