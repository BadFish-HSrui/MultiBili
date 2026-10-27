package tv.hsrui.network.liveFeature.message.fansMedal

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuFansMedalData
import tv.hsrui.network.liveFeature.danmaku.toLiveDanmakuFansMedal
import tv.hsrui.network.login.storage.LoginStorage

suspend fun fetchLiveFansMedalPage(roomId: Long, anchorUid: Long, page: Int): LiveFansMedalPageData = withTimeout(15_000) {
    require(roomId > 0 && anchorUid > 0 && page > 0)
    val response = ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.FANS_MEDAL_PANEL) {
        parameter("room_id", roomId)
        parameter("target_id", anchorUid)
        parameter("page", page)
        parameter("page_size", 10)
        header("Referer", "https://live.bilibili.com/$roomId")
    }
    check(response.status.isSuccess()) { "勋章列表请求失败：HTTP ${response.status.value}" }
    parseLiveFansMedalPage(response.body<JsonObject>(), page)
}

suspend fun fetchLiveCurrentFansMedal(roomId: Long, anchorUid: Long, userId: Long): LiveDanmakuFansMedalData? = withTimeout(15_000) {
    require(roomId > 0 && anchorUid > 0 && userId > 0)
    val response = ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.FANS_MEDAL_CURRENT) {
        parameter("uid", userId)
        parameter("up_uid", anchorUid)
        header("Referer", "https://live.bilibili.com/$roomId")
    }
    check(response.status.isSuccess()) { "当前勋章请求失败：HTTP ${response.status.value}" }
    parseLiveCurrentFansMedal(response.body<JsonObject>())
}

suspend fun wearLiveFansMedal(roomId: Long, anchorUid: Long, medalId: Long): LiveFansMedalOperationData = withTimeout(15_000) {
    require(roomId > 0 && anchorUid > 0 && medalId > 0)
    val storage = getKoin().get<LoginStorage>()
    val csrf = storage.cookies.csrf
    check(storage.isLoggedIn && csrf.isNotEmpty()) { "登录后选择粉丝勋章" }
    val response = ApiClient.httpClient.post(LiveApiUrls.BASE + LiveApiUrls.FANS_MEDAL_WEAR) {
        header("Referer", "https://live.bilibili.com/$roomId")
        header("Origin", "https://live.bilibili.com")
        setBody(FormDataContent(Parameters.build {
            append("medal_id", medalId.toString())
            append("target_id", anchorUid.toString())
            append("csrf", csrf)
            append("csrf_token", csrf)
        }))
    }
    check(response.status.isSuccess()) { "佩戴结果未确认" }
    parseLiveFansMedalOperation(response.body<JsonObject>())
}

suspend fun takeOffLiveFansMedal(roomId: Long, anchorUid: Long): LiveFansMedalOperationData = withTimeout(15_000) {
    require(roomId > 0 && anchorUid > 0)
    val storage = getKoin().get<LoginStorage>()
    val csrf = storage.cookies.csrf
    check(storage.isLoggedIn && csrf.isNotEmpty()) { "登录后选择粉丝勋章" }
    val response = ApiClient.httpClient.post(LiveApiUrls.BASE + LiveApiUrls.FANS_MEDAL_TAKE_OFF) {
        header("Referer", "https://live.bilibili.com/$roomId")
        header("Origin", "https://live.bilibili.com")
        setBody(FormDataContent(Parameters.build {
            append("target_id", anchorUid.toString())
            append("csrf", csrf)
            append("csrf_token", csrf)
        }))
    }
    check(response.status.isSuccess()) { "摘下结果未确认" }
    parseLiveFansMedalOperation(response.body<JsonObject>())
}

internal fun parseLiveFansMedalPage(body: JsonObject, page: Int): LiveFansMedalPageData {
    val data = body.medalData()
    val paging = data["page_info"] as? JsonObject ?: error("勋章分页信息为空")
    val current = paging.int("current_page")?.takeIf { it > 0 } ?: error("勋章页码无效")
    check(current == page) { "勋章页码不匹配" }
    val totalPages = paging.int("total_page")?.takeIf { it >= 0 } ?: error("勋章总页数无效")
    val items = listOf("special_list", "list").flatMap { key ->
        (data[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toMedalItem() }
    }.distinctBy { it.medalId }
    val totalCount = paging.int("total_number") ?: data.int("total_number") ?: 0
    return LiveFansMedalPageData(items, current, totalPages, totalCount.coerceAtLeast(0))
}

internal fun parseLiveCurrentFansMedal(body: JsonObject): LiveDanmakuFansMedalData? {
    val data = body.medalData()
    check(data.containsKey("curr_show")) { "当前勋章数据缺失" }
    val current = data["curr_show"]
    val isEmpty = current == JsonNull || (current as? JsonPrimitive)?.booleanOrNull == false ||
        (current as? JsonObject)?.isEmpty() == true || (current as? JsonArray)?.isEmpty() == true
    if (isEmpty) return null
    return (current as? JsonObject)?.toLiveDanmakuFansMedal() ?: error("当前勋章数据无效")
}

private fun parseLiveFansMedalOperation(body: JsonObject): LiveFansMedalOperationData = LiveFansMedalOperationData(
    code = body.int("code") ?: error("勋章操作结果未确认"),
    message = body.text("message").ifBlank { body.text("msg") },
)

private fun JsonObject.medalData(): JsonObject {
    check(int("code") == 0) { text("message").ifBlank { text("msg").ifBlank { "勋章请求失败" } } }
    return get("data") as? JsonObject ?: error("勋章数据为空")
}

private fun JsonObject.toMedalItem(): LiveFansMedalItemData? {
    val raw = get("medal") as? JsonObject ?: return null
    val medalId = raw.long("medal_id")?.takeIf { it > 0 } ?: return null
    val anchorUid = raw.long("target_id")?.takeIf { it > 0 } ?: return null
    val appearance = (get("uinfo_medal") as? JsonObject)?.toLiveDanmakuFansMedal() ?: buildJsonObject {
        val fields = listOf(
            "name" to "medal_name",
            "level" to "level",
            "ruid" to "target_id",
            "is_light" to "is_lighted",
            "guard_level" to "guard_level",
            "color" to "medal_color",
            "color_start" to "medal_color_start",
            "color_end" to "medal_color_end",
            "color_border" to "medal_color_border",
        )
        for ((target, source) in fields) {
            raw[source]?.let { put(target, it) }
        }
    }.toLiveDanmakuFansMedal() ?: return null
    return LiveFansMedalItemData(
        medalId = medalId,
        anchorUid = anchorUid,
        anchorName = (get("anchor_info") as? JsonObject)?.text("nick_name").orEmpty().ifBlank { "--" },
        roomId = (get("room_info") as? JsonObject)?.long("room_id")?.takeIf { it > 0 },
        medal = appearance.copy(anchorUid = anchorUid),
        isWorn = raw.int("wearing_status") == 1,
    )
}

private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
