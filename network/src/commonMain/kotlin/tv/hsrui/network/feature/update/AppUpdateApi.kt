package tv.hsrui.network.feature.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
