package tv.hsrui.network.feature.player

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchPlayerInfo(avid: Long, cid: Long): PlayerInfoResponse {
    require(avid > 0L) { "avid 必须为正数" }
    require(cid > 0L) { "cid 必须为正数" }
    val accountSession = getKoin().get<LoginStorage>().cookies.sessData
    val response = try {
        withTimeoutOrNull(10_000L) {
            val result = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Play.INFO) {
                buildWithWbi {
                    parameter("aid", avid)
                    parameter("cid", cid)
                }
            }
            check(result.status.isSuccess()) { "播放器信息请求失败：HTTP ${result.status.value}" }
            result.body<PlayerInfoResponse>()
        } ?: PlayerInfoResponse(message = "播放器信息请求超时")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 字幕和历史记录不可用时仍可正常取流播放；失败结果也缓存，避免重复请求。
        PlayerInfoResponse(message = e.message ?: "播放器信息请求失败")
    }
    return response.bindRequest(avid, cid, accountSession)
}
