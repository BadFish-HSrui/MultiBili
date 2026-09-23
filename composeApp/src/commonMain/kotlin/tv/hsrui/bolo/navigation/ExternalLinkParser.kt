package tv.hsrui.bolo.navigation

import io.ktor.http.Url
import tv.hsrui.bolo.model.Vid
import tv.hsrui.network.feature.link.isBilibiliShortLink
import tv.hsrui.network.feature.link.isSupportedExternalLinkUrl

private val externalBvidRegex = Regex("BV[0-9A-Za-z]{10}")
private val externalIdRegex = Regex("(?:BV[0-9A-Za-z]{10}|(?i:av|ss|ep)[0-9]+)")
// 先消费完整 URL，再在其余文本中识别独立编号，不从陌生域名的路径中提取编号。
private val externalCandidateRegex = Regex(
    """[a-zA-Z][a-zA-Z0-9+.-]*://[^\s<>"'()\[\]{}，。！？；、]+|//[^\s<>"'()\[\]{}，。！？；、]+|(?<![\w./@-])(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}/[^\s<>"'()\[\]{}，。！？；、]+|(?<![a-zA-Z0-9_])(?:BV[0-9A-Za-z]{10}|(?i:av|ss|ep)[0-9]+)(?![a-zA-Z0-9_])""",
)

internal fun extractExternalLink(text: String): String? = externalCandidateRegex.findAll(text)
    .map { it.value.trimEnd('.', ',', ';', '!', '?', '：') }
    .map(::normalizeExternalLink)
    .firstOrNull { isBilibiliShortLink(it) || parseExternalLink(it) != null }

private fun normalizeExternalLink(value: String): String = when {
    value.startsWith("//") -> "https:$value"
    !value.contains("://") && !externalIdRegex.matches(value) -> "https://$value"
    else -> value
}

internal fun parseExternalLink(value: String): BoloRoute? {
    val target = value.trim()
    if (externalIdRegex.matches(target)) return parseExternalId(target)
    val normalized = normalizeExternalLink(target)
    if (!isSupportedExternalLinkUrl(normalized)) return null
    val url = runCatching { Url(normalized) }.getOrNull() ?: return null
    val path = url.encodedPath.trim('/').split('/').filter(String::isNotEmpty)
    val host = url.host.lowercase()
    if (url.protocol.name == "bilibili") {
        return when (host) {
            "video" -> (url.parameters["bvid"] ?: path.singleOrNull() ?: url.parameters["aid"])
                ?.let { parseExternalVideo(it, allowNumeric = true) }
            "bangumi" -> when {
                path.size == 2 && path[0] == "season" -> positiveExternalId(path[1])
                    ?.let { BoloRoute.View.Media(seasonId = it) }
                path.size == 2 && path[0] == "play" -> parseExternalMedia(path[1])
                else -> null
            }
            "space" -> path.singleOrNull()?.let(::positiveExternalId)?.let(BoloRoute.User::Space)
            "search" -> externalSearchRoute(url)
            else -> null
        }
    }
    return when (host) {
        "bilibili.com", "www.bilibili.com", "m.bilibili.com" -> when {
            path.size == 2 && path[0] == "video" -> parseExternalVideo(path[1])
            path.size == 3 && path.take(2) == listOf("bangumi", "play") -> parseExternalMedia(path[2])
            else -> null
        }
        "space.bilibili.com" -> path.singleOrNull()?.let(::positiveExternalId)?.let(BoloRoute.User::Space)
        "search.bilibili.com" -> if (path.isEmpty() || path == listOf("all")) externalSearchRoute(url) else null
        else -> null
    }
}

private fun positiveExternalId(value: String): Long? = value.takeIf { it.all(Char::isDigit) }
    ?.toLongOrNull()?.takeIf { it > 0 }

private fun parseExternalVideo(value: String, allowNumeric: Boolean = false): BoloRoute.View.Video? = when {
    externalBvidRegex.matches(value) -> BoloRoute.View.Video(Vid.BVid(value))
    value.startsWith("av", ignoreCase = true) -> positiveExternalId(value.drop(2))
        ?.let { BoloRoute.View.Video(Vid.AVid(it)) }
    allowNumeric -> positiveExternalId(value)?.let { BoloRoute.View.Video(Vid.AVid(it)) }
    else -> null
}

private fun parseExternalMedia(value: String): BoloRoute.View.Media? {
    val id = positiveExternalId(value.drop(2)) ?: return null
    return when (value.take(2).lowercase()) {
        "ss" -> BoloRoute.View.Media(seasonId = id)
        "ep" -> BoloRoute.View.Media(episodeId = id)
        else -> null
    }
}

private fun parseExternalId(value: String): BoloRoute? = parseExternalVideo(value) ?: parseExternalMedia(value)

private fun externalSearchRoute(url: Url): BoloRoute.Search.Results? = url.parameters["keyword"]
    ?.trim()?.takeIf(String::isNotEmpty)?.let(BoloRoute.Search::Results)
