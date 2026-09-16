package tv.hsrui.network.feature.video.collection

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.feature.video.fetchVideoInfo

suspend fun fetchVideoCollectionVideos(
    mid: Long,
    seasonId: Long,
    pageNumber: Int = 1,
    pageSize: Int = 30,
): VideoCollectionVideosResponse = withTimeoutOrNull(15_000) {
    ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_COLLECTION) {
        parameter("mid", mid)
        parameter("season_id", seasonId)
        parameter("page_num", pageNumber)
        parameter("page_size", pageSize)
        parameter("sort_reverse", false)
    }.body<VideoCollectionVideosResponse>()
} ?: error("合集视频请求超时")

suspend fun fetchVideoCollection(mid: Long, seasonId: Long): VideoCollectionData {
    val response = fetchVideoCollectionVideos(mid, seasonId)
    check(response.isSuccess) { response.message.ifBlank { "合集加载失败（${response.code}）" } }
    val summary = checkNotNull(response.collection)
    check(summary.seasonId == seasonId) { "合集信息不匹配，请重试" }
    if (summary.total == 0 && response.videos.isEmpty()) {
        return VideoCollectionData(seasonId = seasonId, title = summary.title, description = summary.description)
    }
    val first = response.videos.firstOrNull { it.avid > 0 } ?: error("合集视频暂不可用，请重试")
    val detail = withTimeoutOrNull(15_000) { fetchVideoInfo(first.avid) } ?: error("合集分段请求超时")
    check(detail.isSuccess) { detail.message.ifBlank { "合集分段加载失败" } }
    val collection = detail.data.collection
    check(collection != null && collection.seasonId == seasonId && collection.sections.isNotEmpty()) {
        "合集分段信息暂不可用，请重试"
    }
    return collection.copy(
        title = collection.title.ifBlank { summary.title },
        description = collection.description.ifBlank { summary.description },
    )
}
