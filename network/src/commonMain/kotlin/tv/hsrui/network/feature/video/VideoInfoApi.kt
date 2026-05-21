package tv.hsrui.network.feature.video

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchVideoInfo(avid: Long): VideoInfoResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_INFO) {
        parameter("aid", avid)
    }
    return response.body()
}

suspend fun fetchVideoInfo(bvid: String): VideoInfoResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_INFO) {
        parameter("bvid", bvid)
    }
    return response.body()
}