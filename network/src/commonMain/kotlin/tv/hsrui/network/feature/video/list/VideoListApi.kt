package tv.hsrui.network.feature.video.list

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchVideoListInfo(type: VideoListType, id: Long): VideoListInfoData = withTimeoutOrNull(15_000) {
    require(id > 0) { "无效的列表 ID" }
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_LIST_INFO) {
        parameter("type", type.value)
        parameter("biz_id", id)
        parameter("tid", 0)
    }.body<VideoListInfoResponse>()
    check(response.isSuccess) { response.message.ifBlank { "列表信息加载失败（${response.code}）" } }
    val data = checkNotNull(response.data)
    check(if (type == VideoListType.Uploads) data.mid == id else data.id == id) { "列表信息不匹配，请重试" }
    data
} ?: error("列表信息请求超时")

suspend fun fetchVideoListVideos(
    type: VideoListType,
    id: Long,
    sort: VideoListSort = VideoListSort.Default,
    descending: Boolean = true,
    cursorId: Long = 0,
    cursorType: Int = 2,
    withCurrent: Boolean = true,
    before: Boolean = false,
): VideoListVideosData = withTimeoutOrNull(15_000) {
    require(id > 0 && (type == VideoListType.Uploads || sort == VideoListSort.Default)) { "无效的列表参数" }
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_LIST_VIDEOS) {
        parameter("mobi_app", "web")
        parameter("type", type.value)
        parameter("biz_id", id)
        parameter("ps", 20)
        parameter("sort_field", sort.value)
        parameter("desc", descending)
        parameter("tid", 0)
        parameter("oid", cursorId)
        parameter("otype", cursorType)
        parameter("with_current", withCurrent)
        parameter("direction", before)
        parameter("preview", 0)
        parameter("use_pn", false)
    }.body<VideoListVideosResponse>()
    check(response.isSuccess) { response.message.ifBlank { "列表视频加载失败（${response.code}）" } }
    checkNotNull(response.data)
} ?: error("列表视频请求超时")
