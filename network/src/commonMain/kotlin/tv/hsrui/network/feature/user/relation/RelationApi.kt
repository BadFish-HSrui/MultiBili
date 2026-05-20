package tv.hsrui.network.feature.user.relation

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchRelationWith(mid: Long): RelationResponse {
    println(mid)
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.RELATION) {
        parameter("mid", mid)
    }
    println(response.body<String>())

    return response.body()
}