package tv.hsrui.network.liveFeature.danmaku

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import tv.hsrui.network.liveFeature.message.LiveMessageData

fun parseLiveDanmakuMessage(message: LiveMessageData): LiveDanmakuData? {
    if (message.command.substringBefore(':') != "DANMU_MSG") return null
    val info = message.payload["info"] as? JsonArray ?: return null
    val meta = info.getOrNull(0) as? JsonArray ?: return null
    val detail = meta.getOrNull(15).asObject()
    val extra = detail?.get("extra").asObject()
    val user = detail?.get("user").asObject()
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
    )
}

internal fun parseLiveDanmakuHistoryItem(item: JsonObject): LiveDanmakuData? {
    val content = item["text"].text() ?: return null
    val type = (item["dm_type"] as? JsonPrimitive)?.intOrNull ?: 0
    val emoticon = if (type == 1) item["emoticon"].asObject()?.toEmoticon(content) else null
    if (content.isBlank() && emoticon == null) return null
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
            ?: item["user"].asObject()?.get("base").asObject()?.get("name").text()?.takeIf { it.isNotBlank() } ?: "--",
        content = content,
        replyUsername = item["reply"].asObject()?.get("reply_uname").text().orEmpty(),
        emoticon = emoticon,
        inlineEmoticons = if (type == 0) item["emots"].asObject().toEmoticons() else emptyMap(),
    )
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
