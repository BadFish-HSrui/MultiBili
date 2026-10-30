package tv.hsrui.network.liveFeature.danmaku

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import tv.hsrui.network.liveFeature.message.LiveMessageData
import kotlin.math.roundToLong
import kotlin.time.Clock

internal fun parseLiveDanmakuSendConfig(data: JsonObject?): LiveDanmakuSendConfigData {
    return LiveDanmakuSendConfigData(
        maxLength = data?.get("danmu").asObject()?.get("length")
            .nonNegativeInt()?.takeIf { it > 0 } ?: 20,
        bubbleColorArgb = data?.get("bubble_color").bubbleColor(),
        bubbleId = data?.get("bubble_id").nonNegativeLong(),
    )
}

internal fun parseLiveDanmakuSendResult(data: JsonObject): LiveDanmakuData? {
    val info = data["mode_info"].asObject() ?: return null
    val extra = info["extra"].asObject() ?: return null
    val id = extra["id_str"].text()?.takeIf { it.isNotBlank() && it != "0" } ?: return null
    val content = extra["content"].text()?.takeIf { it.isNotBlank() } ?: return null
    val user = info["user"].asObject()
    val bubble = user?.get("bubble_box").asObject()
    return LiveDanmakuData(
        id = id,
        sentAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        username = user?.get("base").asObject()?.get("name").text()
            ?.takeIf { it.isNotBlank() } ?: "--",
        content = content,
        mode = LiveDanmakuMode.Scroll,
        fontSize = 25f,
        colorRgb = (extra["color"] as? JsonPrimitive)?.longOrNull?.and(0xFFFFFF) ?: 0xFFFFFF,
        inlineEmoticons = extra["emots"].asObject().toEmoticons(),
        user = user.toLiveDanmakuUser(),
        spaceUrl = extra["space_url"].imageUrl(),
        bubbleColorArgb = bubble?.get("color").bubbleColor(),
        bubbleId = bubble?.get("id").nonNegativeLong(),
    )
}

fun parseLiveDanmakuMessage(message: LiveMessageData): LiveDanmakuData? {
    if (message.command.substringBefore(':') != "DANMU_MSG") return null
    val info = message.payload["info"] as? JsonArray ?: return null
    val meta = info.getOrNull(0) as? JsonArray ?: return null
    val detail = meta.getOrNull(15).asObject()
    val extra = detail?.get("extra").asObject()
    val user = detail?.get("user").asObject()
    val identity = user.toLiveDanmakuUser()
    val legacyUser = info.getOrNull(2) as? JsonArray
    val content = info.getOrNull(1).text() ?: extra?.get("content").text() ?: return null
    val type = (meta.getOrNull(12) as? JsonPrimitive)?.intOrNull
        ?: (extra?.get("dm_type") as? JsonPrimitive)?.intOrNull ?: 0
    val emoticon = if (type == 1) meta.getOrNull(13).asObject()?.toEmoticon(content) else null
    if (content.isBlank() && emoticon == null) return null
    return LiveDanmakuData(
        mode = when ((meta.getOrNull(1) as? JsonPrimitive)?.intOrNull) {
            1, 6 -> LiveDanmakuMode.Scroll
            4 -> LiveDanmakuMode.Bottom
            5 -> LiveDanmakuMode.Top
            else -> null
        },
        fontSize = (meta.getOrNull(2) as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }?.toFloat() ?: 25f,
        colorRgb = (meta.getOrNull(3) as? JsonPrimitive)?.longOrNull?.and(0xFFFFFF) ?: 0xFFFFFF,
        id = extra?.get("id_str").text().orEmpty(),
        sentAtEpochMillis = (meta.getOrNull(4) as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            ?: (info.getOrNull(9).asObject()?.get("ts") as? JsonPrimitive)?.longOrNull
                ?.takeIf { it > 0 }?.times(1_000),
        username = user?.get("base").asObject()?.get("name").text()?.takeIf { it.isNotBlank() }
            ?: legacyUser?.getOrNull(1).text()?.takeIf { it.isNotBlank() } ?: "--",
        content = content,
        replyUsername = extra?.get("reply_uname").text().orEmpty(),
        emoticon = emoticon,
        inlineEmoticons = if (type == 0) extra?.get("emots").asObject().toEmoticons() else emptyMap(),
        spaceUrl = extra?.get("space_url").imageUrl(),
        bubbleColorArgb = meta.getOrNull(11).bubbleColor(),
        bubbleId = meta.getOrNull(17).nonNegativeLong(),
        user = identity.copy(
            uid = identity.uid ?: legacyUser?.getOrNull(0).positiveLong(),
            isRoomAdmin = legacyUser?.getOrNull(2).flag(),
            fansMedal = if (user?.containsKey("medal") == true) identity.fansMedal
                else (info.getOrNull(3) as? JsonArray).toLiveDanmakuFansMedal(),
            guardLevel = if (info.size > 7) info[7].guardLevel() else identity.guardLevel,
            wealthLevel = (info.getOrNull(16) as? JsonArray)?.getOrNull(0).nonNegativeInt()
                ?: identity.wealthLevel,
            userLevel = (info.getOrNull(4) as? JsonArray)?.getOrNull(0).nonNegativeInt(),
        ),
    )
}

internal fun parseLiveDanmakuHistoryItem(item: JsonObject): LiveDanmakuData? {
    val content = item["text"].text() ?: return null
    val type = (item["dm_type"] as? JsonPrimitive)?.intOrNull ?: 0
    val emoticon = if (type == 1) item["emoticon"].asObject()?.toEmoticon(content) else null
    if (content.isBlank() && emoticon == null) return null
    val user = item["user"].asObject()
    val identity = user.toLiveDanmakuUser()
    val timestamp = (item["check_info"].asObject()?.get("ts") as? JsonPrimitive)?.longOrNull
        ?.takeIf { it > 0 }?.times(1_000)
        ?: item["timeline"].text()?.let {
            runCatching {
                LocalDateTime.parse(it.replace(' ', 'T')).toInstant(TimeZone.of("Asia/Shanghai")).toEpochMilliseconds()
            }.getOrNull()
        }
    return LiveDanmakuData(
        id = item["id_str"].text().orEmpty(),
        sentAtEpochMillis = timestamp,
        username = item["nickname"].text()?.takeIf { it.isNotBlank() }
            ?: user?.get("base").asObject()?.get("name").text()?.takeIf { it.isNotBlank() } ?: "--",
        content = content,
        replyUsername = item["reply"].asObject()?.get("reply_uname").text().orEmpty(),
        emoticon = emoticon,
        inlineEmoticons = if (type == 0) item["emots"].asObject().toEmoticons() else emptyMap(),
        spaceUrl = item["space_url"].imageUrl(),
        bubbleColorArgb = item["bubble_color"].bubbleColor(),
        bubbleId = item["bubble_id_v2"].nonNegativeLong(),
        user = identity.copy(
            uid = identity.uid ?: item["uid"].positiveLong(),
            isRoomAdmin = item["isadmin"].flag(),
            fansMedal = if (user?.containsKey("medal") == true) identity.fansMedal
                else (item["medal"] as? JsonArray).toLiveDanmakuFansMedal(),
            guardLevel = if (item.containsKey("guard_level")) item["guard_level"].guardLevel()
                else identity.guardLevel,
            wealthLevel = item["wealth_level"].nonNegativeInt() ?: identity.wealthLevel,
            userLevel = (item["user_level"] as? JsonArray)?.getOrNull(0).nonNegativeInt(),
        ),
    )
}

internal fun parseLiveDanmakuMedalResources(data: JsonObject): LiveDanmakuMedalResourcesData {
    val icons = data["medal_icon"].asObject()
    fun JsonElement?.iconMap() = asObject()?.mapNotNull { (code, value) ->
        val level = JsonPrimitive(code).guardLevel()?.takeUnless { it == LiveDanmakuGuardLevel.None }
        val url = value.imageUrl()
        if (level != null && url != null) level to url else null
    }?.toMap().orEmpty()
    return LiveDanmakuMedalResourcesData(
        guardIcons = icons?.get("guard_icon").iconMap(),
        thousandGuardIcons = icons?.get("thousand_guard_icon").iconMap(),
        thousandGuardAnchorUids = (icons?.get("thousand_guard_uid") as? JsonArray)
            ?.mapNotNull { it.positiveLong() }?.toSet().orEmpty(),
    )
}

internal fun parseLiveDanmakuBubbleResources(data: JsonObject): Map<Long, LiveDanmakuBubbleData> =
    (data["content"].asObject()?.get("danmu_bubble_bg") as? JsonArray)?.mapNotNull { value ->
        val item = value.asObject() ?: return@mapNotNull null
        val id = item["id"].positiveLong() ?: return@mapNotNull null
        val web = item["web"].asObject() ?: return@mapNotNull null
        val url = web["url"].imageUrl() ?: return@mapNotNull null
        val width = web["width"].nonNegativeInt()?.takeIf { it > 2 } ?: return@mapNotNull null
        val height = web["height"].nonNegativeInt()?.takeIf { it > 2 } ?: return@mapNotNull null
        id to LiveDanmakuBubbleData(
            url = url,
            width = width,
            height = height,
            isFleet = (item["business_type"] as? JsonPrimitive)?.intOrNull == 8,
            firstLineUrl = web["first_line_bkg_url"].imageUrl(),
        )
    }?.toMap().orEmpty()

internal fun JsonObject?.toLiveDanmakuUser(): LiveDanmakuUserData = LiveDanmakuUserData(
    uid = this?.get("uid").positiveLong(),
    isMystery = this?.get("base").asObject()?.get("is_mystery").flag(),
    fansMedal = this?.get("medal").asObject()?.toLiveDanmakuFansMedal(),
    guardLevel = this?.get("guard").asObject()?.get("level").guardLevel(),
    wealthLevel = this?.get("wealth").asObject()?.get("level").nonNegativeInt(),
)

internal fun JsonObject.toLiveDanmakuFansMedal(): LiveDanmakuFansMedalData? {
    val name = get("name").text()?.takeIf { it.isNotBlank() } ?: return null
    val level = get("level").nonNegativeInt()?.takeIf { it > 0 } ?: return null
    val color = get("color").rgbColor()
    return LiveDanmakuFansMedalData(
        name = name,
        level = level,
        anchorUid = get("ruid").positiveLong(),
        isLighted = get("is_light").flag() == true,
        startColorArgb = get("v2_medal_color_start").rgbaColor() ?: get("color_start").rgbColor() ?: color,
        endColorArgb = get("v2_medal_color_end").rgbaColor() ?: get("color_end").rgbColor() ?: color,
        borderColorArgb = get("v2_medal_color_border").rgbaColor() ?: get("color_border").rgbColor() ?: color,
        textColorArgb = get("v2_medal_color_text").rgbaColor(),
        guardLevel = get("guard_level").guardLevel(),
    )
}

private fun JsonArray?.toLiveDanmakuFansMedal(): LiveDanmakuFansMedalData? {
    if (this == null) return null
    val name = getOrNull(1).text()?.takeIf { it.isNotBlank() } ?: return null
    val level = getOrNull(0).nonNegativeInt()?.takeIf { it > 0 } ?: return null
    val color = getOrNull(4).rgbColor()
    return LiveDanmakuFansMedalData(
        name = name,
        level = level,
        anchorUid = getOrNull(12).positiveLong(),
        isLighted = getOrNull(11).flag() == true,
        startColorArgb = getOrNull(8).rgbColor() ?: color,
        endColorArgb = getOrNull(9).rgbColor() ?: color,
        borderColorArgb = getOrNull(7).rgbColor() ?: color,
        guardLevel = getOrNull(10).guardLevel(),
    )
}

private fun JsonElement?.nonNegativeInt(): Int? = (this as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }

private fun JsonElement?.positiveLong(): Long? = (this as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }

private fun JsonElement?.nonNegativeLong(): Long? = (this as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }

private fun JsonElement?.imageUrl(): String? {
    val url = text()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https:" + url.removePrefix("http:")
        url.startsWith("https://") -> url
        else -> null
    }
}

private fun JsonElement?.bubbleColor(): Long? {
    val value = text()?.substringBefore(',')?.trim() ?: return null
    if (value.isEmpty()) return 0L
    val hex = value.removePrefix("#")
    if (hex.length != 6 && hex.length != 8 || hex.any { it.digitToIntOrNull(16) == null }) return null
    val color = hex.toLongOrNull(16) ?: return null
    if (hex.length == 6) return color or 0xFF000000
    val alpha = kotlin.math.round((color ushr 24) / 255.0 * 10) / 10
    return (color and 0xFFFFFF) or ((alpha * 255).roundToLong() shl 24)
}

private fun JsonElement?.flag(): Boolean? = (this as? JsonPrimitive)?.let {
    it.booleanOrNull ?: when (it.intOrNull) {
        0 -> false
        1 -> true
        else -> null
    }
}

private fun JsonElement?.guardLevel(): LiveDanmakuGuardLevel? = when ((this as? JsonPrimitive)?.intOrNull) {
    0 -> LiveDanmakuGuardLevel.None
    1 -> LiveDanmakuGuardLevel.Governor
    2 -> LiveDanmakuGuardLevel.Admiral
    3 -> LiveDanmakuGuardLevel.Captain
    else -> null
}

private fun JsonElement?.rgbColor(): Long? = (this as? JsonPrimitive)?.longOrNull
    ?.takeIf { it in 0..0xFFFFFF }?.or(0xFF000000)

private fun JsonElement?.rgbaColor(): Long? {
    val hex = text()?.takeIf { it.startsWith('#') }?.drop(1) ?: return null
    if (hex.length != 6 && hex.length != 8 || hex.any { it.digitToIntOrNull(16) == null }) return null
    val color = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) color or 0xFF000000 else (color ushr 8) or ((color and 0xFF) shl 24)
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.asObject(): JsonObject? = when (this) {
    is JsonObject -> this
    is JsonPrimitive -> contentOrNull?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    else -> null
}

private fun JsonObject.toEmoticon(text: String): LiveDanmakuEmoticonData? {
    val url = get("url").text()?.takeIf { it.isNotBlank() } ?: return null
    return LiveDanmakuEmoticonData(
        url = if (url.startsWith("//")) "https:$url" else url,
        text = text,
        width = (get("width") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: 1,
        height = (get("height") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: 1,
    )
}

private fun JsonObject?.toEmoticons(): Map<String, LiveDanmakuEmoticonData> =
    this?.mapNotNull { (text, value) ->
        if (text.isEmpty()) null else value.asObject()?.toEmoticon(text)?.let { text to it }
    }?.toMap().orEmpty()
