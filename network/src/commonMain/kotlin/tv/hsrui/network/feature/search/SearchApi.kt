package tv.hsrui.network.feature.search

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.ensureBuvid3
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchSearchVideos(
    keyword: String,
    page: Int,
    order: SearchVideoOrder = SearchVideoOrder.Comprehensive,
): SearchVideosResponse {
    ensureBuvid3()
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.SEARCH) {
        buildWithWbi {
            parameter("search_type", SearchCategory.Video.apiValue)
            parameter("keyword", keyword)
            parameter("order", order.apiValue)
            parameter("duration", 0)
            parameter("tids", 0)
            parameter("page", page)
        }
    }
    val raw: RawSearchVideosResponse = response.body()

    return SearchVideosResponse(raw)
}

suspend fun fetchSearchMedia(keyword: String, category: SearchCategory, page: Int): SearchMediaResponse {
    require(category == SearchCategory.Bangumi || category == SearchCategory.Film)
    ensureBuvid3()
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.SEARCH) {
        buildWithWbi {
            parameter("search_type", category.apiValue)
            parameter("keyword", keyword)
            parameter("page", page)
        }
    }
    return SearchMediaResponse(response.body())
}

suspend fun fetchSearchUsers(
    keyword: String,
    page: Int,
    order: SearchUserOrder = SearchUserOrder.Default,
): SearchUsersResponse {
    ensureBuvid3()
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.SEARCH) {
        buildWithWbi {
            parameter("search_type", SearchCategory.User.apiValue)
            parameter("keyword", keyword)
            parameter("page", page)
            order.apiOrder?.let { parameter("order", it) }
            order.sortDirection?.let { parameter("order_sort", it) }
        }
    }
    return SearchUsersResponse(response.body())
}
