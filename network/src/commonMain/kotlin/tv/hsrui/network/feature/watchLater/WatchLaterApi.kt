package tv.hsrui.network.feature.watchLater

import io.ktor.client.call.body
import io.ktor.client.request.get
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchWatchLaterVideos(): WatchLaterResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.WatchLater.LIST)

    return response.body()
}