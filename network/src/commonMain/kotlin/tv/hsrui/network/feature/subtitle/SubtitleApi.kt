package tv.hsrui.network.feature.subtitle

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.model.BaseResponse
import tv.hsrui.network.utils.toHttpsUrl

private val subtitleContentJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun fetchSubtitleInfo(
    avid: Long,
    cid: Long,
    isMedia: Boolean,
    playbackLanguage: String = "",
    playbackProductionType: Int = 0,
    asrLanguage: String? = null,
    ocrLanguage: String? = null,
): SubtitleResponse = withTimeoutOrNull(10_000L) {
    require(avid > 0 && cid > 0) { "字幕请求需要有效的 AID 和 CID" }
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Play.SUBTITLE) {
        parameter("pid", avid)
        parameter("oid", cid)
        parameter("type", 1)
        parameter("context_ext", "{\"video_type\":${if (isMedia) 2 else 1}}")
        parameter("preferred_language", "zh-CN")
        parameter("playlist_switch", 0)
        if (playbackLanguage.isNotBlank()) parameter("cur_language", playbackLanguage)
        parameter("cur_production_type", playbackProductionType)
        if (!asrLanguage.isNullOrBlank()) parameter("arc_video_language", asrLanguage)
        if (!ocrLanguage.isNullOrBlank()) parameter("arc_hardcoded_language", ocrLanguage)
    }
    val bytes = response.bodyAsBytes()
    check(response.status.isSuccess()) { "字幕请求失败：HTTP ${response.status.value}" }
    val contentType = response.contentType()?.withoutParameters()?.toString()?.lowercase()
    val first = bytes.firstOrNull { it.toInt().toChar() !in " \t\r\n" }
    if (contentType == "application/json" || contentType?.endsWith("+json") == true || first == '{'.code.toByte()) {
        val error = subtitleContentJson.decodeFromString<BaseResponse>(bytes.decodeToString())
        error("字幕接口返回 JSON：[${error.code}] ${error.message}")
    }
    ProtoBuf.decodeFromByteArray<SubtitleResponse>(bytes).also {
        check(it.isSuccess) { "字幕响应缺少字幕信息" }
    }
} ?: error("字幕请求超时")

suspend fun fetchSubtitleContent(url: String, isAss: Boolean = false): SubtitleContentResponse = withTimeoutOrNull(15_000L) {
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

    val response = ApiClient.httpClient.get(normalizedUrl)
    check(response.status.isSuccess()) { "字幕内容请求失败：HTTP ${response.status.value}" }
    parseSubtitleContent(response.bodyAsText(), isAss)
} ?: error("字幕内容请求超时")
