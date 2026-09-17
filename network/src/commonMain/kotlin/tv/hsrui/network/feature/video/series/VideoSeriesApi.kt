package tv.hsrui.network.feature.video.series

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchVideoSeries(seriesId: Long): VideoSeriesData {
    val response = withTimeoutOrNull(15_000) {
        ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_SERIES) {
            parameter("series_id", seriesId)
        }.body<VideoSeriesResponse>()
    } ?: error("系列信息请求超时")
    check(response.isSuccess) { response.message.ifBlank { "系列加载失败（${response.code}）" } }
    val series = checkNotNull(response.series)
    check(series.seriesId == seriesId && series.mid > 0) { "系列信息不匹配，请重试" }
    return series
}

suspend fun fetchVideoSeriesVideos(
    mid: Long,
    seriesId: Long,
    pageNumber: Int = 1,
    pageSize: Int = 20,
): VideoSeriesVideosResponse = withTimeoutOrNull(15_000) {
    ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.VIDEO_SERIES_VIDEOS) {
        parameter("mid", mid)
        parameter("series_id", seriesId)
        parameter("pn", pageNumber)
        parameter("ps", pageSize)
        parameter("sort", "desc")
        parameter("only_normal", true)
    }.body<VideoSeriesVideosResponse>()
} ?: error("系列视频请求超时")
