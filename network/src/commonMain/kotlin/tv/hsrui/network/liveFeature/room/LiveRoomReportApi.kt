package tv.hsrui.network.liveFeature.room

import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import kotlinx.coroutines.withTimeout
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.BaseResponse

suspend fun reportLiveHistory(roomId: Long): BaseResponse = withTimeout(10_000L) {
    require(roomId > 0) { "直播间 ID 无效" }
    val storage = getKoin().get<LoginStorage>()
    if (!storage.isLoggedIn || storage.cookies.csrf.isEmpty()) return@withTimeout BaseResponse()
    ApiClient.httpClient.post(LiveApiUrls.BASE + LiveApiUrls.ROOM_HISTORY_REPORT) {
        expectSuccess = true
        header("Referer", "https://live.bilibili.com/$roomId")
        header("Origin", "https://live.bilibili.com")
        setBody(FormDataContent(Parameters.build {
            append("room_id", roomId.toString())
            append("platform", "pc")
            append("csrf", storage.cookies.csrf)
            append("csrf_token", storage.cookies.csrf)
            append("visit_id", "")
            append("web_location", "444.8")
        }))
    }.body()
}

suspend fun reportLiveRoomEntry(roomId: Long): BaseResponse = withTimeout(10_000L) {
    require(roomId > 0) { "直播间 ID 无效" }
    if (!getKoin().get<LoginStorage>().isLoggedIn) return@withTimeout BaseResponse()
    ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.ROOM_ENTRY_REPORT) {
        expectSuccess = true
        header("Referer", "https://live.bilibili.com/$roomId")
        parameter("room_id", roomId)
        parameter("from", 0)
        parameter("not_mock_enter_effect", 0)
    }.body()
}
