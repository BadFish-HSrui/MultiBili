package tv.hsrui.network.feature.player

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray

// CDN 请求不携带登录 API 的 Cookie；索引读取与后续 VLC 媒体请求使用相同 headers。
private val dashIndexClient by lazy { HttpClient() }

data class DashIndexResponse(val bytes: ByteArray, val resourceLength: Long)

suspend fun fetchDashIndex(
    url: String,
    start: Long,
    endInclusive: Long,
    headers: Map<String, String>,
): DashIndexResponse {
    require(start >= 0 && endInclusive >= start && endInclusive - start < 1_048_576L) {
        "DASH 索引范围无效或过大"
    }
    val expectedLength = endInclusive - start + 1L
    return withTimeoutOrNull(15_000L) {
        dashIndexClient.prepareGet(url) {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=$start-$endInclusive")
            header("Accept-Encoding", "identity")
        }.execute { response ->
            check(response.status.value == 206) { "DASH 索引请求未返回部分内容：HTTP ${response.status.value}" }
            val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
                .matchEntire(response.headers["Content-Range"].orEmpty())
            check(range != null && range.groupValues[1].toLongOrNull() == start &&
                range.groupValues[2].toLongOrNull() == endInclusive) { "DASH 索引响应范围不匹配" }
            val resourceLength = checkNotNull(range.groupValues[3].toLongOrNull())
            check(resourceLength > endInclusive) { "DASH 媒体长度无效" }
            val bytes = response.bodyAsChannel().readRemaining(expectedLength + 1L).readByteArray()
            check(bytes.size.toLong() == expectedLength) { "DASH 索引响应长度不匹配" }
            DashIndexResponse(bytes, resourceLength)
        }
    } ?: error("DASH 索引请求超时")
}
