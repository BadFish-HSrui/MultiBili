package tv.hsrui.network.feature.video.tags

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchVideoTags(avid: Long): VideoTagsResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_TAGS) {
        parameter("aid", avid)
    }
    return response.body()
}
