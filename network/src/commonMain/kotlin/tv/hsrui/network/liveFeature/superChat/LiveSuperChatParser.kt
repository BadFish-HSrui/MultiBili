package tv.hsrui.network.liveFeature.superChat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuFansMedalData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuGuardLevel
import tv.hsrui.network.liveFeature.danmaku.toLiveDanmakuUser
import tv.hsrui.network.liveFeature.message.LiveMessageData

fun parseLiveSuperChatMessage(message: LiveMessageData): LiveSuperChatData? =
    if (message.command.substringBefore(':') == "SUPER_CHAT_MESSAGE") {
        (message.payload["data"] as? JsonObject)?.toLiveSuperChat()
    } else null

internal fun parseLiveSuperChatList(info: JsonObject?): List<LiveSuperChatData> =
    (info?.get("message_list") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toLiveSuperChat() }.orEmpty()

fun parseLiveSuperChatDeletedIds(message: LiveMessageData): Set<String> {
    if (message.command.substringBefore(':') != "SUPER_CHAT_MESSAGE_DELETE") return emptySet()
    val data = message.payload["data"] as? JsonObject ?: return emptySet()
    return (data["ids"] as? JsonArray)?.mapNotNull { it.messageId() }?.toSet().orEmpty()
}

private fun JsonObject.toLiveSuperChat(): LiveSuperChatData? {
    val id = get("id").messageId() ?: return null
    val content = get("message").text()?.takeIf { it.isNotBlank() } ?: return null
    val amount = get("price").text()?.takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) } ?: return null
    val start = get("start_time").positiveLong()
    val end = get("end_time").positiveLong()?.takeIf { it <= Long.MAX_VALUE / 1_000 }
    val time = get("time").positiveInt()
    val duration = if (start != null && end != null && end > start) {
        (end - start).takeIf { it <= Int.MAX_VALUE }?.toInt()
    } else null
    val total = duration ?: time ?: return null
    val uinfo = get("uinfo") as? JsonObject
    val base = uinfo?.get("base") as? JsonObject
    val legacy = get("user_info") as? JsonObject
    val identity = uinfo.toLiveDanmakuUser()
    val medal = get("medal_info") as? JsonObject
    return LiveSuperChatData(
        id = id,
        content = content,
        amountYuan = amount,
        username = base?.get("name").text()?.takeIf { it.isNotBlank() }
            ?: legacy?.get("uname").text()?.takeIf { it.isNotBlank() } ?: "--",
        avatarUrl = base?.get("face").avatarUrl() ?: legacy?.get("face").avatarUrl().orEmpty(),
        user = identity.copy(
            uid = identity.uid ?: get("uid").positiveLong(),
            isRoomAdmin = legacy?.get("manager").flag(),
            isMystery = identity.isMystery ?: get("is_mystery").flag() ?: legacy?.get("is_mystery").flag(),
            fansMedal = if (uinfo?.containsKey("medal") == true) identity.fansMedal else medal?.toFansMedal(),
            guardLevel = identity.guardLevel ?: legacy?.get("guard_level").guardLevel(),
            userLevel = legacy?.get("user_level").positiveInt(),
        ),
        totalSeconds = total,
        endTimeEpochSeconds = end.takeIf { duration != null },
        backgroundColorArgb = get("background_color").colorArgb(),
        bottomColorArgb = get("background_bottom_color").colorArgb(),
        messageColorArgb = get("message_font_color").colorArgb(),
    )
}

private fun JsonObject.toFansMedal(): LiveDanmakuFansMedalData? {
    val name = get("medal_name").text()?.takeIf { it.isNotBlank() } ?: return null
    val level = get("medal_level").positiveInt() ?: return null
    val color = get("medal_color").colorArgb()
    return LiveDanmakuFansMedalData(
        name = name,
        level = level,
        anchorUid = get("target_id").positiveLong(),
        isLighted = get("is_lighted").flag() == true,
        startColorArgb = get("medal_color_start").colorArgb() ?: color,
        endColorArgb = get("medal_color_end").colorArgb() ?: color,
        borderColorArgb = get("medal_color_border").colorArgb() ?: color,
        guardLevel = get("guard_level").guardLevel(),
    )
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.messageId(): String? = text()?.takeIf { value ->
    value.isNotEmpty() && value.all { it in '0'..'9' }
}?.trimStart('0')?.takeIf { it.isNotEmpty() }

private fun JsonElement?.positiveLong(): Long? = (this as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }

private fun JsonElement?.positiveInt(): Int? = (this as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }

private fun JsonElement?.flag(): Boolean? = (this as? JsonPrimitive)?.let {
    it.booleanOrNull ?: when (it.intOrNull) { 0 -> false; 1 -> true; else -> null }
}

private fun JsonElement?.guardLevel(): LiveDanmakuGuardLevel? = when ((this as? JsonPrimitive)?.intOrNull) {
    0 -> LiveDanmakuGuardLevel.None
    1 -> LiveDanmakuGuardLevel.Governor
    2 -> LiveDanmakuGuardLevel.Admiral
    3 -> LiveDanmakuGuardLevel.Captain
    else -> null
}

private fun JsonElement?.colorArgb(): Long? {
    val value = text()?.trim() ?: return null
    val color = if (value.startsWith('#')) {
        value.drop(1).takeIf { it.length == 6 }?.toLongOrNull(16)
    } else value.toLongOrNull()
    return color?.takeIf { it in 0..0xFFFFFF }?.or(0xFF000000)
}

private fun JsonElement?.avatarUrl(): String? = text()?.trim()?.let {
    when {
        it.startsWith("https://") -> it
        it.startsWith("http://") -> "https:" + it.removePrefix("http:")
        it.startsWith("//") -> "https:$it"
        else -> null
    }
}
