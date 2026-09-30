package tv.hsrui.network.feature.link

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.constant.BOLO_UA

private val externalLinkHosts = setOf(
    "bilibili.com", "www.bilibili.com", "m.bilibili.com",
    "space.bilibili.com", "search.bilibili.com", "b23.tv",
)

// 外部短链不使用带有账号 Cookie 的 API 客户端，也不自动跟随重定向。
private val externalLinkClient by lazy { HttpClient { followRedirects = false } }

fun isSupportedExternalLinkUrl(value: String): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    if (url.user != null || url.password != null) return false
    return when (url.protocol.name) {
        "http", "https" -> url.host.lowercase() in externalLinkHosts && url.port == url.protocol.defaultPort
        "bilibili" -> url.host.lowercase() in setOf("video", "bangumi", "space", "search")
        else -> false
    }
}

fun isBilibiliShortLink(value: String): Boolean {
    if (!isSupportedExternalLinkUrl(value)) return false
    val url = Url(value)
    return url.host.equals("b23.tv", ignoreCase = true) && url.encodedPath.trim('/').isNotEmpty()
}

suspend fun fetchExternalLinkRedirect(url: String): String? = fetchExternalLinkRedirect(url) { current ->
    externalLinkClient.prepareGet(current) {
        header(HttpHeaders.UserAgent, BOLO_UA)
    }.execute { response ->
        response.status.value to response.headers[HttpHeaders.Location]
    }
}

internal suspend fun fetchExternalLinkRedirect(
    url: String,
    request: suspend (String) -> Pair<Int, String?>,
): String? = withTimeoutOrNull(10_000L) {
    if (!isBilibiliShortLink(url)) return@withTimeoutOrNull null
    var current = URLBuilder(url).apply { protocol = URLProtocol.HTTPS; port = 443 }.buildString()
    val visited = mutableSetOf<String>()
    repeat(5) {
        if (!visited.add(current)) return@withTimeoutOrNull null
        val (status, location) = request(current)
        if (status !in setOf(301, 302, 303, 307, 308) || location.isNullOrBlank()) {
            return@withTimeoutOrNull null
        }
        val next = runCatching { URLBuilder(current).takeFrom(location).buildString() }.getOrNull()
            ?: return@withTimeoutOrNull null
        if (!isSupportedExternalLinkUrl(next)) return@withTimeoutOrNull null
        if (!isBilibiliShortLink(next)) return@withTimeoutOrNull next
        current = URLBuilder(next).apply { protocol = URLProtocol.HTTPS; port = 443 }.buildString()
    }
    null
}
