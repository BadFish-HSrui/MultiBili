package tv.hsrui.network.feature.user.info

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchUserInfo(mid: Long): UserInfoResponse {
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.USER_INFO) {
        buildWithWbi { parameter("mid", mid) }
    }.body()
}

suspend fun fetchUserUpStat(mid: Long): UserUpStatResponse {
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.USER_UP_STAT) {
        parameter("mid", mid)
    }.body()
}
