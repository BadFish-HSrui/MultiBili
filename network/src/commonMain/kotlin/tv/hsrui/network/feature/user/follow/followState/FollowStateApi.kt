package tv.hsrui.network.feature.user.follow.followState

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchFollowState(mid: Long): FollowState {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.FOLLOW_STATE) {
        parameter("vmid", mid)
    }
    val result: FollowState = response.body()
    return result
}