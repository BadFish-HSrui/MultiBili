package tv.hsrui.network.liveFeature.recommend

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchLiveRecommend(page: Int = 1, pageSize: Int = 30): LiveRecommendResponse {
    return ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.RECOMMEND) {
        buildWithWbi {
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("platform", "web")
        }
        header("Referer", "https://live.bilibili.com/all")
    }.body()
}
