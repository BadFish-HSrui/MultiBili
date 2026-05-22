package tv.hsrui.network.feature.related.video

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchRelatedVideosFor(avid: Long): RelatedVideosResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_RELATED) {
        parameter("aid", avid)
    }
    return response.body()
}

suspend fun fetchRelatedVideosFor(bvid: String): RelatedVideosResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_RELATED) {
        parameter("bvid", bvid)
    }
    return response.body()
}