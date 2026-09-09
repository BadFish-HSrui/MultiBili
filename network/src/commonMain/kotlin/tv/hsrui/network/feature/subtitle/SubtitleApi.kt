package tv.hsrui.network.feature.subtitle

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.utils.toHttpsUrl
import tv.hsrui.network.wbi.buildWithWbi

private const val SUBTITLE_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/140.0.0.0 Safari/537.36"

// 字幕 CDN 请求不携带登录 API 的 Cookie。
private val subtitleContentClient by lazy { HttpClient() }
private val subtitleContentJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

suspend fun fetchSubtitleList(avid: Long, cid: Long): SubtitleListResponse {
    require(avid > 0) { "avid 必须为正数" }
    require(cid > 0) { "cid 必须为正数" }

    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Subtitle.LIST) {
        buildWithWbi {
            parameter("aid", avid)
            parameter("cid", cid)
        }
    }
    check(response.status.isSuccess()) { "字幕列表请求失败：HTTP ${response.status.value}" }
    return response.body<SubtitleListResponse>()
}

suspend fun fetchSubtitleContent(url: String): SubtitleContentResponse {
    val normalizedUrl = url.toHttpsUrl()
    require(normalizedUrl.isNotBlank()) { "字幕地址不能为空" }
    require(normalizedUrl.startsWith("https://", ignoreCase = true) ||
        normalizedUrl.startsWith("http://", ignoreCase = true)) {
        "字幕地址必须为 HTTP(S) URL"
    }
    val parsedUrl = Url(normalizedUrl)
    require((parsedUrl.protocol == URLProtocol.HTTP || parsedUrl.protocol == URLProtocol.HTTPS) &&
        parsedUrl.host.isNotBlank() && parsedUrl.host.none { it.isWhitespace() }) {
        "字幕地址必须包含有效的 HTTP(S) 主机"
    }

    val response = subtitleContentClient.get(normalizedUrl) {
        header("User-Agent", SUBTITLE_USER_AGENT)
        header("Referer", "https://www.bilibili.com/")
    }
    check(response.status.isSuccess()) { "字幕内容请求失败：HTTP ${response.status.value}" }
    return subtitleContentJson.decodeFromString<SubtitleContentResponse>(response.bodyAsText())
}
