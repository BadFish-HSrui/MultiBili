package tv.hsrui.network.feature.dynamic

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

private suspend fun fetchDynamicRaw(pn :Int, type: String , offset: String): DynamicRawResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.DYNAMIC) {
        parameter("type", type)
        parameter("offset", offset)
        parameter("page", pn)
    }
    val result: DynamicRawResponse = response.body()

    return  result
}

suspend fun fetchFollowingVideos(pn: Int = 1,offset: String = ""): FollowingVideosResponse {
    val response = fetchDynamicRaw(pn = pn, type = "video", offset = offset)

    return FollowingVideosResponse(raw = response)
}

suspend fun fetchFollowingVideoUpdates(updateBaseline: String): DynamicUpdateResponse =
    ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.DYNAMIC + "/update") {
        parameter("type", "video")
        parameter("update_baseline", updateBaseline)
    }.body()
