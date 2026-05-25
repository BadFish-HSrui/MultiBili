package tv.hsrui.network.feature.video.actions.state

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchVideoActionsStateFor(bvid: String): VideoActionsStateResponse {
    val likeResponse = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VideoAction.HAS_LIKE) {
        parameter("bvid",bvid)
    }.body<VideoLikeStateResponse>()
    val coinResponse = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VideoAction.HAS_COIN) {
        parameter("bvid",bvid)
    }.body<VideoCoinStateResponse>()
    val favouredResponse = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VideoAction.HAS_FAVOURED) {
        parameter("aid",bvid)
    }.body<VideoFavouredStateResponse>()

    val message = buildString {
        if (!likeResponse.isSuccess) append("[${likeResponse.code}]: ${likeResponse.message}")
        if (!likeResponse.isSuccess && !coinResponse.isSuccess) appendLine()
        if (!coinResponse.isSuccess) append("[${coinResponse.code}]: ${coinResponse.message}")
        if (!coinResponse.isSuccess && !favouredResponse.isSuccess) appendLine()
        if (!favouredResponse.isSuccess) append("[${favouredResponse.code}]: ${favouredResponse.message}")
    }

    return VideoActionsStateResponse(
        message = message,
        isLiked = likeResponse.isLiked,
        coinedCount = coinResponse.coinedCount,
        isFavoured = favouredResponse.isFavoured
    )

}