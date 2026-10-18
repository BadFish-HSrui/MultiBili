package tv.hsrui.network.feature.danmaku

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.BaseResponse
import tv.hsrui.network.wbi.buildWithWbi
import kotlin.time.Clock

@OptIn(ExperimentalSerializationApi::class)
suspend fun fetchDanmakuSegment(
    cid: Long,
    segmentIndex: Long = 1,
    avid: Long? = null,
): DanmakuSegmentResponse {
    require(cid > 0) { "cid 必须为正数" }
    require(segmentIndex > 0) { "segmentIndex 必须为正数" }
    require(avid == null || avid > 0) { "avid 必须为正数" }

    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Danmaku.SEGMENT) {
        buildWithWbi {
            parameter("type", 1)
            parameter("oid", cid)
            parameter("segment_index", segmentIndex)
            if (avid != null) parameter("pid", avid)
        }
    }
    return ProtoBuf.decodeFromByteArray<DanmakuSegmentResponse>(response.danmakuBytes())
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun fetchDanmakuView(avid: Long, cid: Long): DanmakuViewResponse = withTimeout(10_000L) {
    require(avid > 0L && cid > 0L) { "视频标识必须为正数" }
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Danmaku.VIEW) {
        parameter("type", 1)
        parameter("oid", cid)
        parameter("pid", avid)
    }
    ProtoBuf.decodeFromByteArray<DanmakuViewResponse>(response.danmakuBytes())
}

suspend fun sendDanmaku(avid: Long, cid: Long, progressMs: Long, message: String): SendDanmakuResponse =
    withTimeoutOrNull(15_000L) {
        require(avid > 0L && cid > 0L && progressMs >= 0L) { "弹幕发送位置无效" }
        require(message.isNotBlank() && message.length <= 100) { "弹幕内容必须为 1 至 100 字" }
        val storage = getKoin().get<LoginStorage>()
        val cookies = storage.cookies
        check(cookies.sessData.isNotEmpty() && cookies.csrf.isNotEmpty()) { "请先登录" }
        val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Danmaku.SEND) {
            buildWithWbi {
                parameter("csrf", cookies.csrf)
                parameter("web_location", "1315873")
            }
            setBody(FormDataContent(Parameters.build {
                append("type", "1")
                append("oid", cid.toString())
                append("aid", avid.toString())
                append("msg", message)
                append("progress", progressMs.toString())
                append("mode", "1")
                append("pool", "0")
                append("color", "16777215")
                append("fontsize", "25")
                append("rnd", (Clock.System.now().toEpochMilliseconds() * 1_000L).toString())
                append("plat", "1")
                append("csrf", cookies.csrf)
            }))
        }
        check(response.status.isSuccess()) { "弹幕发送失败：HTTP ${response.status.value}" }
        response.body<SendDanmakuResponse>()
    } ?: error("弹幕发送超时，结果未确认")

suspend fun fetchDanmakuFilters(): DanmakuFilterListResponse = withTimeoutOrNull(15_000L) {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Danmaku.FILTER_LIST)
    check(response.status.isSuccess()) { "弹幕屏蔽列表读取失败：HTTP ${response.status.value}" }
    response.body<DanmakuFilterListResponse>()
} ?: error("弹幕屏蔽列表读取超时")

suspend fun addDanmakuFilter(filter: DanmakuFilterData): DanmakuFilterAddResponse =
    withTimeoutOrNull(15_000L) {
        require(filter.content.isNotBlank()) { "弹幕屏蔽内容不能为空白" }

        val cookies = getKoin().get<LoginStorage>().cookies
        check(cookies.sessData.isNotEmpty() && cookies.csrf.isNotEmpty()) { "请先登录" }
        val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Danmaku.FILTER_ADD) {
            setBody(FormDataContent(Parameters.build {
                append("type", filter.type.code.toString())
                append("filter", filter.content)
                append("csrf", cookies.csrf)
            }))
        }
        check(response.status.isSuccess()) { "弹幕屏蔽规则添加失败：HTTP ${response.status.value}" }
        response.body<DanmakuFilterAddResponse>()
    } ?: error("弹幕屏蔽规则添加超时，结果未确认")

suspend fun deleteDanmakuFilter(id: Long): BaseResponse = withTimeoutOrNull(15_000L) {
    require(id > 0L) { "弹幕屏蔽规则 ID 必须为正数" }
    val cookies = getKoin().get<LoginStorage>().cookies
    check(cookies.sessData.isNotEmpty() && cookies.csrf.isNotEmpty()) { "请先登录" }
    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Danmaku.FILTER_DELETE) {
        setBody(FormDataContent(Parameters.build {
            append("ids", id.toString())
            append("csrf", cookies.csrf)
        }))
    }
    check(response.status.isSuccess()) { "弹幕屏蔽规则删除失败：HTTP ${response.status.value}" }
    response.body<BaseResponse>()
} ?: error("弹幕屏蔽规则删除超时，结果未确认")

private suspend fun HttpResponse.danmakuBytes(): ByteArray {
    check(status.isSuccess()) { "弹幕请求失败：HTTP ${status.value}" }
    return bodyAsBytes()
}
