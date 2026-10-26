package tv.hsrui.network.liveFeature.danmaku

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.wbi.buildWithWbi
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Clock

suspend fun fetchLiveDanmakuSendConfig(roomId: Long): LiveDanmakuSendConfigData = withTimeout(15_000) {
    require(roomId > 0) { "直播间 ID 无效" }
    val response = ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.DANMAKU_SEND_CONFIG) {
        buildWithWbi {
            parameter("room_id", roomId)
            parameter("web_location", "444.8")
        }
        header("Referer", "https://live.bilibili.com/$roomId")
    }
    check(response.status.isSuccess()) { "发送配置请求失败：HTTP ${response.status.value}" }
    val body = response.body<JsonObject>()
    check((body["code"] as? JsonPrimitive)?.intOrNull == 0) {
        (body["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "发送配置请求失败"
    }
    val property = (body["data"] as? JsonObject)?.get("property") as? JsonObject
    val danmaku = property?.get("danmu") as? JsonObject
    LiveDanmakuSendConfigData((danmaku?.get("length") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: 20)
}

suspend fun sendLiveDanmaku(roomId: Long, message: String): LiveDanmakuData {
    require(roomId > 0) { "直播间 ID 无效" }
    require(message.isNotBlank()) { "请输入弹幕内容" }
    val storage = getKoin().get<LoginStorage>()
    val csrf = storage.cookies.csrf
    check(storage.isLoggedIn && csrf.isNotEmpty()) { "登录后发送弹幕" }
    val uncertain = "结果未确认，请勿重复发送"
    val body = try {
        withTimeout(15_000) {
            val response = ApiClient.httpClient.post(LiveApiUrls.BASE + LiveApiUrls.DANMAKU_SEND) {
                buildWithWbi { parameter("web_location", "444.8") }
                header("Referer", "https://live.bilibili.com/$roomId")
                header("Origin", "https://live.bilibili.com")
                setBody(FormDataContent(Parameters.build {
                    append("roomid", roomId.toString())
                    append("msg", message)
                    append("rnd", Clock.System.now().epochSeconds.toString())
                    append("fontsize", "25")
                    append("color", "16777215")
                    append("mode", "1")
                    append("bubble", "0")
                    append("room_type", "0")
                    append("csrf", csrf)
                    append("csrf_token", csrf)
                }))
            }
            check(response.status.isSuccess()) { uncertain }
            response.body<JsonObject>()
        }
    } catch (error: CancellationException) {
        if (error !is TimeoutCancellationException) throw error
        error(uncertain)
    } catch (_: Exception) {
        error(uncertain)
    }
    val code = (body["code"] as? JsonPrimitive)?.intOrNull ?: error(uncertain)
    check(code == 0) {
        listOf("message", "msg").firstNotNullOfOrNull {
            (body[it] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        } ?: "弹幕发送失败（$code）"
    }
    return (body["data"] as? JsonObject)?.let(::parseLiveDanmakuSendResult) ?: error(uncertain)
}

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
