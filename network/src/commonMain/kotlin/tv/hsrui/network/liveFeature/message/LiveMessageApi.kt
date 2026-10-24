package tv.hsrui.network.liveFeature.message

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.login.ensureBuvid3
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchLiveMessageInfo(roomId: Long): LiveMessageInfoData = withTimeout(15_000) {
    require(roomId > 0) { "直播间 ID 无效" }
    ensureBuvid3()
    val storage = getKoin().get<LoginStorage>()
    val identity = storage.cookies
    val response = ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.MESSAGE_INFO) {
        buildWithWbi { parameter("id", roomId); parameter("type", 0) }
        header("Referer", "https://live.bilibili.com/$roomId")
    }
    check(response.status.isSuccess()) { "直播信息流请求失败：HTTP ${response.status.value}" }
    val raw = response.body<RawLiveMessageResponse>()
    check(raw.code == 0) { "直播信息流请求失败（${raw.code}）" }
    check(storage.cookies == identity) { "直播信息流身份已变化" }
    val data = checkNotNull(raw.data) { "直播信息流连接信息为空" }
    check(data.token.isNotBlank()) { "直播信息流凭证为空" }
    val hostPattern = Regex("[A-Za-z0-9.-]+\\.chat\\.bilibili\\.com")
    val urls = data.hosts.filter { hostPattern.matches(it.host) && it.port in 1..65535 }
        .map { "wss://${it.host}:${it.port}/sub" }.distinct()
        .ifEmpty { listOf("wss://broadcastlv.chat.bilibili.com:443/sub") }
    LiveMessageInfoData(data.token, urls, if (identity.sessData.isNotEmpty()) identity.dedeUserID else 0, identity.buvid3)
}
