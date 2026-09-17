package tv.hsrui.network.feature.subtitle

import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.serialization.json.Json
import tv.hsrui.network.utils.toHttpsUrl

private val subtitleJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
private val subtitleUrlKeys = listOf(
    "nP](wOFRvU.+<fjS{jn-!\$D|Dz&\",zT`" to "=CFxYRn{.y|uVyO\$uh&sikph?N.ilF/`",
    "Bn\"q~|albg@]Go~ACgyDvKnd+)_D}^&J?" to "Cu~L!xs~f^&r@'vh=q]q{eeng*sEg^kp#J",
)

internal fun resolveSubtitleUrl(rawUrl: String): String = runCatching {
    val normalized = rawUrl.toHttpsUrl()
    if (!normalized.startsWith("https://", true) && !normalized.startsWith("http://", true)) return ""
    val url = Url(normalized)
    if (url.host.isBlank() || url.host.any { it.isWhitespace() } || url.user != null || url.password != null) return ""
    if (!url.host.equals("subtitle.bilibili.com", true)) return normalized
    val token = url.encodedPath.removePrefix("/").decodeURLPart()
    for ((prefix, secret) in subtitleUrlKeys) {
        val key = secret + "bilibili"
        val decoded = token.mapIndexed { index, char -> (char.code xor key[index % key.length].code).toChar() }.joinToString("")
        if (decoded.startsWith(prefix)) {
            val path = decoded.removePrefix(prefix)
            if (!path.startsWith('/') || path.any { it.isISOControl() }) return ""
            return "https://aisubtitle.hdslb.com" + path + normalized.substringAfter('?', "").let {
                if (it.isEmpty()) "" else "?$it"
            }
        }
    }
    ""
}.getOrDefault("")

internal fun parseSubtitleContent(content: String, isAss: Boolean): SubtitleContentResponse =
    if (isAss) parseAssContent(content) else subtitleJson.decodeFromString<SubtitleContentResponse>(content)

/**
 * 当前实际上没有发现使用ass字幕的 视频/番剧/影视，ass转换效果未测试
 */
private fun parseAssContent(content: String): SubtitleContentResponse {
    var inEvents = false
    var fields = emptyList<String>()
    val cues = mutableListOf<SubtitleCue>()
    for (rawLine in content.lineSequence()) {
        val line = rawLine.trim().removePrefix("\uFEFF")
        if (line.startsWith('[')) {
            inEvents = line.equals("[Events]", true)
            continue
        }
        if (!inEvents) continue
        if (line.startsWith("Format:", true)) {
            fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
            continue
        }
        if (!line.startsWith("Dialogue:", true) || fields.lastOrNull() != "text") continue
        val values = line.substringAfter(':').trimStart().split(',', limit = fields.size)
        if (values.size != fields.size) continue
        fun field(name: String): String? = fields.indexOf(name).takeIf { it >= 0 }?.let { values[it].trim() }
        val start = parseAssTime(field("start")) ?: continue
        val end = parseAssTime(field("end")) ?: continue
        if (end <= start) continue
        val text = assPlainText(values.last())
        if (text.isNotBlank()) cues += SubtitleCue(start, end, text)
    }
    return SubtitleContentResponse(cues)
}

private fun parseAssTime(value: String?): Double? {
    val parts = value?.split(':') ?: return null
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val minutes = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val seconds = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < 60 } ?: return null
    return hours * 3600.0 + minutes * 60.0 + seconds
}

private fun assPlainText(value: String): String {
    val text = StringBuilder()
    var drawing = false
    var index = 0
    while (index < value.length) {
        if (value[index] == '{') {
            val end = value.indexOf('}', index + 1)
            if (end < 0) break
            Regex("\\\\p(\\d+)(?![\\d.])").findAll(value.substring(index + 1, end)).forEach {
                drawing = it.groupValues[1].toIntOrNull() != 0
            }
            index = end + 1
        } else if (value[index] == '\\' && index + 1 < value.length) {
            val next = value[index + 1]
            if (!drawing) {
                text.append(when (next) {
                    'N', 'n' -> "\n"
                    'h' -> " "
                    else -> "\\$next"
                })
            }
            index += 2
        } else {
            if (!drawing) text.append(value[index])
            index++
        }
    }
    return text.toString().trim()
}
