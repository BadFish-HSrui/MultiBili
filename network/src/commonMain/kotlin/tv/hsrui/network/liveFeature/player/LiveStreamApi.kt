package tv.hsrui.network.liveFeature.player

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import tv.hsrui.network.constant.BOLO_UA

// 与账号客户端隔离；清单、重定向及媒体正文均不携带账号 Cookie。
private val liveStreamClient by lazy {
    HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }
}

suspend fun fetchLiveHlsPlaylist(
    url: String,
    roomId: Long,
    stream: LivePlayStreamData? = null,
): LiveHlsPlaylistData = withTimeout(15_000) {
    val pending = ArrayDeque<Pair<String, Int>>().apply { add(url to 0) }
    val visited = mutableSetOf<String>()
    var failure: Exception? = null
    repeat(12) {
        if (pending.isEmpty()) throw failure ?: IllegalStateException("直播媒体清单不可用")
        val (address, depth) = pending.removeFirst()
        if (!visited.add(address)) return@repeat
        try {
            check(depth < 4) { "直播清单嵌套层数过多" }
            val content = Buffer()
            val response = fetchLiveStream(address, roomId, receiveLimit = 1024 * 1024) { bytes, offset, length ->
                content.write(bytes, offset, offset + length)
            }
            val text = content.readByteArray().decodeToString()
            val variants = text.lineSequence().count { it.trim().startsWith("#EXT-X-STREAM-INF:") }
            if (variants == 0) return@withTimeout parseLiveHlsPlaylist(text, response.url)
            val selected = checkNotNull(stream) { "直播多画质清单缺少目标画质和编码" }
            // 单一变体可以继承上层选择，多画质清单必须声明实际档位与编码。
            val urls = parseLiveHlsStreams(text, response.url, selected, inheritSelection = variants == 1)
                .filter { it.currentQn == selected.currentQn }.flatMap { it.urls }
            check(urls.isNotEmpty()) { "直播嵌套清单不包含所选画质和编码" }
            urls.asReversed().forEach { pending.addFirst(it to depth + 1) }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            failure = error
        }
    }
    error("直播清单跳转次数过多")
}

/** write 返回前不会复用正文块；背压等待不计入网络读取超时。 */
suspend fun fetchLiveStream(
    url: String,
    roomId: Long,
    range: LongRange? = null,
    receiveLimit: Long? = null,
    onResponse: suspend (LiveStreamResponseData) -> Unit = {},
    write: suspend (ByteArray, Int, Int) -> Unit,
): LiveStreamResponseData = coroutineScope {
    require(roomId > 0 && (url.startsWith("https://") || url.startsWith("http://"))) { "直播媒体地址无效" }
    require(range == null || range.first >= 0 && range.last >= range.first && range.last < Long.MAX_VALUE)
    require(receiveLimit == null || receiveLimit > 0)
    val deadline = launch {
        delay(15_000)
        error("直播媒体连接超时")
    }
    try {
        liveStreamClient.prepareGet(url) {
            header(HttpHeaders.UserAgent, BOLO_UA)
            header(HttpHeaders.Referrer, "https://live.bilibili.com/$roomId")
            header(HttpHeaders.AcceptEncoding, "identity")
            if (range != null) header(HttpHeaders.Range, "bytes=${range.first}-${range.last}")
        }.execute { response ->
            deadline.cancel()
            val status = response.status.value
            check(status == 200 || status == 206) { "直播媒体请求失败：HTTP $status" }
            val encoding = response.headers[HttpHeaders.ContentEncoding]
            check(encoding == null || encoding.equals("identity", true)) { "直播媒体内容编码无效" }
            val length = response.headers[HttpHeaders.ContentLength]?.let {
                checkNotNull(it.toLongOrNull()?.takeIf { size -> size >= 0 }) { "直播媒体长度无效" }
            }
            val expected = if (range != null) {
                val match = response.headers[HttpHeaders.ContentRange]?.let {
                    Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(it)
                }
                check(status == 206 && match != null &&
                    match.groupValues[1].toLongOrNull() == range.first &&
                    match.groupValues[2].toLongOrNull() == range.last &&
                    (match.groupValues[3] == "*" || (match.groupValues[3].toLongOrNull() ?: 0) > range.last)) {
                    "直播媒体范围与请求不一致"
                }
                val size = range.last - range.first + 1
                check(length == null || length == size) { "直播媒体范围长度无效" }
                size
            } else {
                check(status == 200) { "直播媒体返回了未请求的范围" }
                length
            }
            check(receiveLimit == null || expected == null || expected <= receiveLimit) { "直播媒体超过接收上限" }
            val metadata = LiveStreamResponseData(status, response.call.request.url.toString(),
                range?.first ?: 0, range?.last?.plus(1) ?: length)
            onResponse(metadata)
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var received = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val remaining = minOf(expected ?: Long.MAX_VALUE, receiveLimit ?: Long.MAX_VALUE) - received
                // 到达声明长度或上限后只读一个字节以确认 EOF，不能把截断响应算作成功。
                val count = withTimeout(10_000) {
                    channel.readAvailable(buffer, 0, if (remaining <= 0) 1 else minOf(buffer.size.toLong(), remaining).toInt())
                }
                if (count < 0) {
                    check(expected == null || received == expected) { "直播媒体响应提前结束" }
                    break
                }
                if (count == 0) continue
                check(remaining > 0) { "直播媒体超过接收上限或声明长度" }
                received += count
                write(buffer, 0, count)
            }
            metadata
        }
    } finally {
        deadline.cancel()
    }
}
