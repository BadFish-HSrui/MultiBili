package tv.hsrui.network.feature.danmaku

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.model.BaseResponse
import tv.hsrui.network.wbi.buildWithWbi

private val danmakuErrorJson = Json { ignoreUnknownKeys = true }

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
    val bytes = response.bodyAsBytes()
    val contentType = response.contentType()?.withoutParameters()?.toString()?.lowercase()
    val firstByte = bytes.firstOrNull {
        it != ' '.code.toByte() && it != '\t'.code.toByte() &&
            it != '\n'.code.toByte() && it != '\r'.code.toByte()
    }
    if (contentType == "application/json" || contentType?.endsWith("+json") == true || firstByte == '{'.code.toByte()) {
        val errorResponse = danmakuErrorJson.decodeFromString<BaseResponse>(bytes.decodeToString())
        error("弹幕接口返回 JSON，HTTP ${response.status.value}：[${errorResponse.code}] ${errorResponse.message}")
    }
    check(response.status.isSuccess()) { "弹幕请求失败：HTTP ${response.status.value}" }
    check(
        contentType == null || contentType == "application/octet-stream" ||
            contentType == "application/protobuf" || contentType == "application/x-protobuf"
    ) {
        "弹幕接口返回非预期类型：$contentType"
    }
    return ProtoBuf.decodeFromByteArray<DanmakuSegmentResponse>(bytes)
}
