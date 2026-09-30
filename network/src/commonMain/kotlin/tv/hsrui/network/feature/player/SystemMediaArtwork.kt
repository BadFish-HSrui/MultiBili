package tv.hsrui.network.feature.player

import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.utils.toHttpsUrl

/** 封面复用 API 客户端，并限制时间与响应体大小。 */
suspend fun fetchSystemMediaArtwork(url: String): ByteArray = withTimeout(10_000L) {
    val parsed = Url(url.toHttpsUrl())
    require(parsed.protocol == URLProtocol.HTTPS || parsed.protocol == URLProtocol.HTTP)
    ApiClient.httpClient.prepareGet(parsed.toString()).execute { response ->
        check(response.status.isSuccess())
        val limit = 4 * 1024 * 1024
        val bytes = response.bodyAsChannel().readRemaining((limit + 1).toLong()).readByteArray()
        check(bytes.isNotEmpty() && bytes.size <= limit) { "媒体封面大小超出限制" }
        bytes
    }
}
