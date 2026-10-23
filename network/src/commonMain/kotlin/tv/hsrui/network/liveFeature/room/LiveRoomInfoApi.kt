package tv.hsrui.network.liveFeature.room

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchLiveRoomInfo(roomId: Long): LiveRoomInfoData = withTimeout(15_000) {
    require(roomId > 0) { "直播间 ID 无效" }
    val response = ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.ROOM_INFO) {
        buildWithWbi { parameter("room_id", roomId) }
        header("Referer", "https://live.bilibili.com/$roomId")
    }
    check(response.status.isSuccess()) { "直播间信息请求失败：HTTP ${response.status.value}" }
    response.body<RawLiveRoomInfoResponse>().toLiveRoomInfo()
}
