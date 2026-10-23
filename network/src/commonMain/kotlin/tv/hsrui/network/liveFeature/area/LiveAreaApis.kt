package tv.hsrui.network.liveFeature.area

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchLiveAreaList(): LiveAreaListResponse {
    return ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.AREA_LIST) {
        buildWithWbi {
            parameter("source_id", 1)
        }
        header("Referer", "https://live.bilibili.com/")
    }.body()
}
