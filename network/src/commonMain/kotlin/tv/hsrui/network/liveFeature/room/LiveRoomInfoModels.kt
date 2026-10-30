package tv.hsrui.network.liveFeature.room

import tv.hsrui.network.liveFeature.superChat.LiveSuperChatData
import tv.hsrui.network.liveFeature.superChat.parseLiveSuperChatList
import tv.hsrui.network.utils.toHttpsUrl

data class LiveRoomInfoData(
    val roomId: Long,
    val title: String,
    val upMid: Long,
    val upName: String,
    val coverUrl: String,
    val isLive: Boolean,
    val liveStartTimeEpochSeconds: Long? = null,
    val upAvatarUrl: String = "",
    val followerCount: Long? = null,
    val watchedCount: Long? = null,
    val popularity: Long? = null,
    val description: String = "",
    val areaId: Int? = null,
    val areaName: String = "",
    val parentAreaId: Int? = null,
    val parentAreaName: String = "",
    val announcement: String = "",
    val superChats: List<LiveSuperChatData> = emptyList(),
    val shortRoomId: Long? = null,
)

internal fun RawLiveRoomInfoResponse.toLiveRoomInfo(): LiveRoomInfoData {
    check(code == 0) { "直播间信息获取失败（$code）：$message" }
    val info = checkNotNull(data) { "直播间信息为空" }
    val room = checkNotNull(info.roomInfo) { "直播间信息为空" }
    val anchor = checkNotNull(info.anchorInfo?.baseInfo) { "主播信息为空" }
    check(room.roomId > 0 && room.uid > 0 && room.title.isNotBlank() && anchor.name.isNotBlank()) {
        "直播间信息不完整"
    }
    return LiveRoomInfoData(
        roomId = room.roomId,
        shortRoomId = room.shortRoomId?.takeIf { it >= 0L },
        title = room.title,
        upMid = room.uid,
        upName = anchor.name,
        coverUrl = room.cover.ifBlank { room.keyframe }.trim().toHttpsUrl(),
        isLive = room.liveStatus == 1,
        liveStartTimeEpochSeconds = room.liveStartTimeEpochSeconds?.takeIf { it > 0 },
        upAvatarUrl = anchor.avatarUrl.trim().toHttpsUrl(),
        followerCount = info.anchorInfo?.relationInfo?.followerCount?.takeIf { it >= 0 },
        watchedCount = info.watchedShow?.count?.takeIf { it >= 0 },
        popularity = room.popularity?.takeIf { it >= 0 },
        description = room.description.toLiveRoomDescription(),
        areaId = room.areaId?.takeIf { it > 0 },
        areaName = room.areaName.trim(),
        parentAreaId = room.parentAreaId?.takeIf { it > 0 },
        parentAreaName = room.parentAreaName.trim(),
        announcement = info.newsInfo?.content.orEmpty().toLiveRoomDescription(),
        superChats = parseLiveSuperChatList(info.superChatInfo),
    )
}

private fun String.toLiveRoomDescription(): String = decodeLiveRoomEntities()
    .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    .replace(Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    .replace(Regex("</?(?:p|div|br|hr|li|ul|ol|h[1-6]|blockquote|tr)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</?[a-z][^>]*>", RegexOption.IGNORE_CASE), "")
    .decodeLiveRoomEntities()
    .replace('\u00a0', ' ')
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")

private fun String.decodeLiveRoomEntities(): String =
    Regex("&(#x[0-9a-f]+|#[0-9]+|[a-z]+);", RegexOption.IGNORE_CASE).replace(this) { match ->
        val entity = match.groupValues[1]
        val replacement = when (entity) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp", "ensp", "emsp", "thinsp" -> " "
            "ndash" -> "–"
            "mdash" -> "—"
            "hellip" -> "…"
            "bull" -> "•"
            "copy" -> "©"
            "reg" -> "®"
            "trade" -> "™"
            else -> {
                val codePoint = when {
                    entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)
                    entity.startsWith('#') -> entity.drop(1).toIntOrNull()
                    else -> null
                }
                when {
                    codePoint == null || codePoint !in 1..0x10FFFF || codePoint in 0xD800..0xDFFF -> null
                    codePoint <= 0xFFFF -> codePoint.toChar().toString()
                    else -> buildString {
                        append(((codePoint - 0x10000) / 0x400 + 0xD800).toChar())
                        append(((codePoint - 0x10000) % 0x400 + 0xDC00).toChar())
                    }
                }
            }
        }
        replacement ?: match.value
    }
