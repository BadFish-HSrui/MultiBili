package tv.hsrui.network.feature.history

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchHistoryRow(
    loadParams: HistoryLoadParams,
    typeString: String,
    ps: Int
): HistoryRawResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.HISTORY) {
        parameter("max", loadParams.max)
        parameter("view_at", loadParams.viewAt)
        parameter("business", loadParams.business)
        parameter("type", typeString)
        parameter("ps", ps)
    }
    val result: HistoryRawResponse = response.body()

    return result
}

suspend fun fetchHistoryVideos(
    loadParams: HistoryLoadParams = HistoryLoadParams(),
    ps: Int = 30
): HistoryVideosResponse {
    val response = fetchHistoryRow(
        loadParams = loadParams,
        typeString = "archive",
        ps = ps
    )
    return HistoryVideosResponse(
        raw = response,
        canLoadMore = (response.data.list.size == ps)
    )
}