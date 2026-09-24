package tv.hsrui.network.feature.player

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchVideoPlayInfo(
    avid: Long,
    cid: Long
): VideoSource {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Play.VIDEO) {
        buildWithWbi {
            parameter("avid", avid)
            parameter("cid", cid)
            parameter("qn", 120)
            parameter("fnval", 2192)
            parameter("fourk", 1)
            parameter("try_look", 1)
            parameter("voice_balance", 1)
        }
    }

    return response.body<VideoPlayResponse>().toVideoSource()
}
