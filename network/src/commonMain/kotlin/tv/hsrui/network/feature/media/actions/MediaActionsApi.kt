package tv.hsrui.network.feature.media.actions

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchMediaActionsState(episodeId: Long): MediaActionsStateResponse {
    require(episodeId > 0) { "缺少有效的集标识" }
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.MEDIA_ACTIONS) {
        parameter("ep_id", episodeId)
    }.body()
}

suspend fun fetchMediaCoinLimit(episodeId: Long): MediaCoinLimitResponse {
    require(episodeId > 0) { "缺少有效的集标识" }
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.MEDIA_COIN_LIMIT) {
        parameter("ep_id", episodeId)
    }.body()
}
