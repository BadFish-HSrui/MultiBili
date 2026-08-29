package tv.hsrui.network.feature.search

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.ensureBuvid3
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchSearchVideos(keyword: String, page: Int): SearchVideosResponse {
    ensureBuvid3()
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.SEARCH) {
        buildWithWbi {
            parameter("search_type", "video")
            parameter("keyword", keyword)
            parameter("order", "totalrank")
            parameter("duration", 0)
            parameter("tids", 0)
            parameter("page", page)
        }
    }
    val raw: RawSearchVideosResponse = response.body()

    return SearchVideosResponse(raw)
}
