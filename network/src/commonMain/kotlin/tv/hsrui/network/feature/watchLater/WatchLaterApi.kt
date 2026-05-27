package tv.hsrui.network.feature.watchLater

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun fetchWatchLaterVideos(): WatchLaterResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.WatchLater.LIST)

    return response.body()
}

suspend fun addWatchLater(avid: Long): AddWatchLaterResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.WatchLater.ADD) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("aid", avid.toString())
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}