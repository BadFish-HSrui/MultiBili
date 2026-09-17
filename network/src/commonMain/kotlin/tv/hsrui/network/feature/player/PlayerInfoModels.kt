package tv.hsrui.network.feature.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PlayerInfoResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: PlayerInfoData? = null,
) {
    // 请求上下文不进入 JSON、copy 或 toString，也不持久化账号会话。
    @Transient private var requestMedia: Pair<Long, Long>? = null
    @Transient private var requestAccountSession: String? = null

    val isSuccess: Boolean get() = code == 0 && data != null
    val needLoginSubtitle: Boolean? get() = data?.needLoginSubtitle
    val asrLanguage: String? get() = data?.asrLanguage.takeIf { isSuccess }
    val ocrLanguage: String? get() = data?.ocrLanguage.takeIf { isSuccess }
    val lastPlayCid: Long get() = if (isSuccess) data?.lastPlayCid ?: 0L else 0L
    val lastPlayPositionMs: Long get() = if (isSuccess) data?.lastPlayPositionMs ?: 0L else 0L

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
)
