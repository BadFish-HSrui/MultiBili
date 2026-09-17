package tv.hsrui.network.feature.media

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchMediaIndex(
    seasonType: Int = 1,
    page: Int = 1,
    selection: MediaFilterSelection = MediaFilterSelection(),
): MediaIndexResponse {
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.MEDIA_INDEX) {
        parameter("season_type", seasonType)
        parameter("type", 1)
        parameter("pagesize", 24)
        parameter("page", page)
        selection.queryParameters.forEach { (field, value) -> parameter(field, value) }
    }.body()
}

suspend fun fetchMediaConditions(seasonType: Int): MediaConditionsResponse {
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.MEDIA_CONDITIONS) {
        parameter("season_type", seasonType)
        parameter("type", 1)
    }.body()
}

suspend fun fetchMediaSeason(seasonId: Long = 0, episodeId: Long = 0): MediaSeasonResponse {
    require(seasonId > 0 || episodeId > 0) { "缺少有效的媒体标识" }
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.MEDIA_SEASON) {
        if (episodeId > 0) parameter("ep_id", episodeId)
        else parameter("season_id", seasonId)
    }.body()
}

suspend fun fetchRelatedMedia(seasonId: Long): MediaRecommendationsResponse {
    return ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.MEDIA_RELATED) {
        parameter("season_id", seasonId)
    }.body()
}
