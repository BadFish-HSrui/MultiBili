package tv.hsrui.network.feature.popular

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchPopularVideos(pn: Int = 1,ps: Int = 20): PopularResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.POPULAR) {
        parameter("pn",pn)
        parameter("ps",ps)
    }
    val result: PopularResponse = response.body()
    return result
}