package tv.hsrui.network.feature.player

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tv.hsrui.network.constant.BOLO_UA

// 播放媒体不携带 API 客户端的账号 Cookie；连接由引擎复用，请求归播放会话所有。
private val playerStreamClient by lazy {
    HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }
}

suspend fun fetchPlayerStream(
    url: String,
    start: Long,
    endExclusive: Long,
    validator: String? = null,
    continueFullResponse: Boolean = false,
    receiveLimit: Long? = null,
    onResponse: suspend (PlayerStreamResponseData) -> Unit,
    onReceived: suspend (Int) -> Unit,
    write: suspend (Long, ByteArray, Int, Int) -> Unit,
    strictRange: Boolean = false,
    headerTimeoutMillis: Long = 15_000,
    readTimeoutMillis: Long = 30_000,
    requestTimeoutMillis: Long? = null,
): PlayerStreamResponseData {
    require(url.startsWith("https://") || url.startsWith("http://")) { "播放地址无效" }
    require(start >= 0 && endExclusive > start && endExclusive - start <= 4L * 1024 * 1024)
    require(receiveLimit == null || receiveLimit > 0)
    require(headerTimeoutMillis > 0 && readTimeoutMillis > 0)
    require(requestTimeoutMillis == null || requestTimeoutMillis > 0)
    var responseStatus: Int? = null
    val attempt: suspend () -> PlayerStreamResponseData = {
        coroutineScope {
            val deadline = launch {
                delay(headerTimeoutMillis)
                throw PlayerStreamException("播放连接超时", true)
            }
            try {
                playerStreamClient.prepareGet(url) {
                    timeout { connectTimeoutMillis = headerTimeoutMillis }
                    header(HttpHeaders.UserAgent, BOLO_UA)
                    header(HttpHeaders.Referrer, "https://www.bilibili.com")
                    header(HttpHeaders.AcceptEncoding, "identity")
                    header(HttpHeaders.Range, "bytes=$start-${endExclusive - 1}")
                    if (validator != null) header(HttpHeaders.IfRange, validator)
                }.execute { response ->
                    deadline.cancel()
                    val status = response.status.value
                    responseStatus = status
                    fun invalid(message: String): Nothing = throw PlayerStreamException(message, false, statusCode = status)
                    if (strictRange && status == 200) invalid("媒体服务端未返回范围响应")
                    if (status != 200 && status != 206 && status != 416) {
                        throw PlayerStreamException("播放请求失败：HTTP $status",
                            status in 500..599 || status == 412 || status == 429, statusCode = status)
                    }
                    val length = response.headers[HttpHeaders.ContentLength]?.let {
                        it.toLongOrNull()?.takeIf { value -> value >= 0 } ?: invalid("媒体长度无效")
                    }
                    val encoding = response.headers[HttpHeaders.ContentEncoding]
                    if (encoding != null && !encoding.equals("identity", true)) invalid("媒体响应使用了不支持的内容编码")
                    val range = response.headers[HttpHeaders.ContentRange]
                    val etag = response.headers[HttpHeaders.ETag]
                    val modified = response.headers[HttpHeaders.LastModified]
                    val metadata = when (status) {
                        206 -> {
                            val match = range?.let { Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(it) }
                                ?: invalid("媒体范围响应无效")
                            val first = match.groupValues[1].toLongOrNull() ?: invalid("媒体范围起点无效")
                            val last = match.groupValues[2].toLongOrNull() ?: invalid("媒体范围终点无效")
                            val total = match.groupValues[3].let { if (it == "*") null else it.toLongOrNull() ?: invalid("媒体总长度无效") }
                            if (first != start || last < first || last == Long.MAX_VALUE ||
                                last + 1 != minOf(endExclusive, total ?: endExclusive) ||
                                (total != null && total <= last) || (length != null && length != last - first + 1)) {
                                invalid("媒体范围与请求不一致")
                            }
                            PlayerStreamResponseData(status, first, last + 1, total, etag, modified)
                        }
                        200 -> PlayerStreamResponseData(status, 0, length, length, etag, modified)
                        else -> {
                            val total = range?.let { Regex("bytes \\*/(\\d+)").matchEntire(it) }
                                ?.groupValues?.get(1)?.toLongOrNull() ?: invalid("媒体文件尾无法确认")
                            if (start < total) invalid("媒体范围被服务端拒绝")
                            PlayerStreamResponseData(status, start, start, total, etag, modified)
                        }
                    }
                    onResponse(metadata)
                    if (status == 416) return@execute metadata
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var position = metadata.start
                    var received = 0L
                    val stop = if (status == 200 && continueFullResponse) metadata.endExclusive ?: Long.MAX_VALUE
                        else minOf(endExclusive, metadata.endExclusive ?: endExclusive)
                    while (position < stop) {
                        currentCoroutineContext().ensureActive()
                        val available = receiveLimit?.minus(received) ?: Long.MAX_VALUE
                        if (available <= 0) invalid("媒体请求超过接收上限")
                        val count = try {
                            withTimeout(readTimeoutMillis) { channel.readAvailable(buffer, 0, minOf(buffer.size.toLong(), stop - position, available).toInt()) }
                        } catch (error: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            throw PlayerStreamException("播放读取超时", true, error, statusCode = status)
                        }
                        if (count < 0) {
                            if (metadata.endExclusive != null && position != metadata.endExclusive) {
                                throw PlayerStreamException("媒体响应提前结束", true, statusCode = status)
                            }
                            return@execute metadata.copy(endExclusive = position, totalBytes = position)
                        }
                        if (count == 0) continue
                        received += count
                        onReceived(count)
                        val skip = (start - position).coerceIn(0L, count.toLong()).toInt()
                        if (skip < count) write(position + skip, buffer, skip, count - skip)
                        position += count
                    }
                    if (status == 206) {
                        var trailing: Int
                        do {
                            // 上限等于声明正文长度时仍须探测 EOF；额外字节只用于拒绝超界响应。
                            trailing = try { withTimeout(readTimeoutMillis) { channel.readAvailable(buffer, 0, 1) } }
                            catch (error: TimeoutCancellationException) {
                                currentCoroutineContext().ensureActive()
                                throw PlayerStreamException("媒体范围响应未结束", true, error, statusCode = status)
                            }
                        } while (trailing == 0)
                        if (trailing > 0) {
                            onReceived(trailing)
                            invalid("媒体响应超出声明范围")
                        }
                    }
                    // 200 的未消费后缀随 scoped execute 关闭，不缓存整个文件。
                    metadata
                }
            } finally {
                deadline.cancel()
            }
        }
    }
    return if (requestTimeoutMillis == null) attempt() else try {
        withTimeout(requestTimeoutMillis) { attempt() }
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw PlayerStreamException("播放请求超时", true, error, statusCode = responseStatus)
    }
}
