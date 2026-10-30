package tv.hsrui.network.liveFeature.following

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls

suspend fun fetchLiveFollowing(page: Int = 1): LiveFollowingResponse {
    return ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.FOLLOWING) {
        parameter("page", page)
        parameter("page_size", 20)
        parameter("ignoreRecord", 1)
        parameter("hit_ab", true)
    }.body<LiveFollowingResponse>().copy(requestPage = page)
}