package tv.hsrui.network.feature.user.space

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchUserSpacePrivacy(mid: Long): UserSpacePrivacyResponse = withTimeoutOrNull(15_000) {
    ApiClient.httpClient.get(ApiUrls.UserSpace.PRIVACY) {
        parameter("mid", mid)
    }.body<UserSpacePrivacyResponse>()
} ?: error("空间权限请求超时")

suspend fun fetchUserSpaceUploads(
    mid: Long,
    pageNumber: Int = 1,
    order: UserSpaceUploadOrder = UserSpaceUploadOrder.Latest,
): UserSpaceUploadsResponse = withTimeoutOrNull(15_000) {
    ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.UserSpace.UPLOADS) {
        buildWithWbi {
            parameter("mid", mid)
            parameter("pn", pageNumber)
            parameter("ps", 30)
            parameter("order", order.value)
        }
    }.body<UserSpaceUploadsResponse>()
} ?: error("视频投稿请求超时")

suspend fun fetchUserSpaceCollections(
    mid: Long,
    pageNumber: Int = 1,
    pageSize: Int = 20,
): UserSpaceCollectionsResponse = withTimeoutOrNull(15_000) {
    ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.UserSpace.COLLECTIONS) {
        parameter("mid", mid)
        parameter("page_num", pageNumber)
        parameter("page_size", pageSize)
    }.body<UserSpaceCollectionsResponse>()
} ?: error("视频合集请求超时")

suspend fun fetchUserSpaceLikes(mid: Long): UserSpaceLikesResponse = withTimeoutOrNull(15_000) {
    ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.UserSpace.LIKES) { parameter("vmid", mid) }
        .body<UserSpaceLikesResponse>()
} ?: error("最近点赞请求超时")

suspend fun fetchUserSpaceCoins(mid: Long): UserSpaceCoinsResponse = withTimeoutOrNull(15_000) {
    ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.UserSpace.COINS) { parameter("vmid", mid) }
        .body<UserSpaceCoinsResponse>()
} ?: error("最近投币请求超时")
