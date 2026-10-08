package tv.hsrui.network.feature.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

// GitHub 请求使用独立客户端，避免携带 B 站账号 Cookie。
private val appUpdateClient by lazy {
    HttpClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout)
    }
}

suspend fun fetchLatestAppRelease(): AppReleaseData =
    appUpdateClient.get("https://api.github.com/repos/BadFish-HSrui/MultiBili/releases/latest") {
        header(HttpHeaders.UserAgent, "MultiBili")
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2026-03-10")
        timeout { requestTimeoutMillis = 15_000L }
    }.body()

suspend fun downloadAppReleaseAsset(
    url: String,
    write: suspend (ByteArray, Int) -> Unit,
    onProgress: suspend (Long, Long?) -> Unit,
) = coroutineScope {
    require(url.startsWith("https://")) { "安装包下载地址无效" }
    val connectionDeadline = launch {
        delay(15_000)
        error("安装包下载连接超时")
    }
    try {
        appUpdateClient.prepareGet(url) {
            header(HttpHeaders.UserAgent, "MultiBili")
            header(HttpHeaders.AcceptEncoding, "identity")
            timeout {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }.execute { response ->
            connectionDeadline.cancel()
            check(response.status.value == 200) { "下载失败：HTTP ${response.status.value}" }
            val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0 }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var received = 0L
            var lastProgress = TimeSource.Monotonic.markNow()
            onProgress(received, total)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = try {
                    withTimeout(30_000) { channel.readAvailable(buffer) }
                } catch (_: TimeoutCancellationException) {
                    error("安装包下载超过 30 秒未收到数据")
                }
                if (count < 0) break
                if (count == 0) continue
                write(buffer, count)
                received += count
                check(total == null || received <= total) { "安装包大小不匹配" }
                if (lastProgress.elapsedNow().inWholeMilliseconds >= 200) {
                    onProgress(received, total)
                    lastProgress = TimeSource.Monotonic.markNow()
                }
            }
            check(received > 0 && (total == null || received == total)) { "安装包下载不完整" }
            onProgress(received, total ?: received)
        }
    } finally {
        connectionDeadline.cancel()
    }
}
