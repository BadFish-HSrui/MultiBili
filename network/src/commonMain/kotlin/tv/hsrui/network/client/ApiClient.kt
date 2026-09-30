package tv.hsrui.network.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.constant.BOLO_UA
import tv.hsrui.network.login.storage.LoginStorage

object ApiClient {
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
                header("User-Agent", BOLO_UA)
                header("Referer", "https://www.bilibili.com/")
                if (loginStorage.hasCookies) {
                    header("Cookie", loginStorage.getCookiesString())
                }
            }
        }
    }
}
