package tv.hsrui.network.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.login.storage.LoginStorage

object ApiClient {
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/140.0.0.0 Safari/537.36"

    val httpClient: HttpClient by lazy {
        val loginStorage: LoginStorage = getKoin().get()
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(DefaultRequest) {
                header("User-Agent", USER_AGENT)
                header("Referer", "https://www.bilibili.com/")
                if (loginStorage.hasCookies) {
                    header("Cookie", loginStorage.getCookiesString())
                }
            }
        }
    }
}
