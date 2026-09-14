package tv.hsrui.network.feature.player

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchMediaPlayInfo(episodeId: Long): VideoSource {
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Play.MEDIA) {
        parameter("ep_id", episodeId)
        parameter("fnval", 16)
        parameter("fnver", 0)
        parameter("fourk", 1)
    }.body<MediaPlayResponse>().toVideoSource()
}
