package tv.hsrui.network.feature.history

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun fetchHistoryRaw(
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
    require(ps in 1..30)
    var cursor = loadParams
    val visitedCursors = mutableSetOf<HistoryLoadParams>()
    while (true) {
        visitedCursors += cursor
        val response = fetchHistoryRaw(loadParams = cursor, typeString = "all", ps = ps)
        val nextCursor = HistoryLoadParams(
            max = response.data.cursor.max,
            viewAt = response.data.cursor.viewAt,
            business = response.data.cursor.business,
        )
        val result = HistoryVideosResponse(
            raw = response,
            canLoadMore = response.data.list.size == ps && nextCursor !in visitedCursors,
        )
        if (!result.isSuccess || result.validData.list.isNotEmpty() || !result.validData.canLoadMore) {
            return result
        }
        cursor = nextCursor
    }
}

suspend fun searchHistoryVideos(keyword: String, pageNumber: Int = 1): HistorySearchResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.HISTORY_SEARCH) {
        parameter("keyword", keyword)
        parameter("pn", pageNumber)
        parameter("business", "all")
    }
    return response.body()
}

suspend fun deleteHistory(typeString: String, id: Long): DeleteHistoryResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response =  ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.HISTORY_DELETE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("kid", "${typeString}_${id}")
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}