package tv.hsrui.network.feature.region

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchRegionFeed(fromRegion: Int, displayId: Int = 1, requestCnt: Int = 15): RegionResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.REGION) {
        parameter("display_id", displayId)
        parameter("request_cnt", requestCnt)
        parameter("from_region", fromRegion)
    }
    val result: RegionResponse = response.body()
    return result
}