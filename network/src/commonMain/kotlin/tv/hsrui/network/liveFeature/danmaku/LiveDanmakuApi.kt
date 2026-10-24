package tv.hsrui.network.liveFeature.danmaku

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchLiveDanmakuHistory(roomId: Long): List<LiveDanmakuData> = withTimeout(15_000) {
    require(roomId > 0) { "直播间 ID 无效" }
    val response = ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.DANMAKU_HISTORY) {
        buildWithWbi {
            parameter("roomid", roomId)
            parameter("room_type", 0)
        }
        header("Referer", "https://live.bilibili.com/$roomId")
    }
    check(response.status.isSuccess()) { "历史弹幕请求失败：HTTP ${response.status.value}" }
    val body = response.body<JsonObject>()
    val code = (body["code"] as? JsonPrimitive)?.intOrNull
    check(code == 0) { "历史弹幕请求失败（$code）" }
    val room = (body["data"] as? JsonObject)?.get("room") as? JsonArray
        ?: error("历史弹幕数据为空")
    room.mapNotNull { (it as? JsonObject)?.let(::parseLiveDanmakuHistoryItem) }
}
