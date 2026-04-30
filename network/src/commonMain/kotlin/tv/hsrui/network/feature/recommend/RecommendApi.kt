package tv.hsrui.network.feature.recommend

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchRecommendVideos(freshIndex: Int = 1, ps: Int = 30): RecommendResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.RECOMMEND) {
        parameter("fresh_idx", freshIndex)
        parameter("ps", ps)
    }
    val raw: RawRecommendResponse = response.body()

    return RecommendResponse(raw)
}