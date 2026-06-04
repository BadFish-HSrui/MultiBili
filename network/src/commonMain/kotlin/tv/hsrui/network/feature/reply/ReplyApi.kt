package tv.hsrui.network.feature.reply

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.wbi.buildWithWbi

suspend fun fetchRepliesWith(
    replySection: ReplySectionType,
    sort: ReplySort,
    loadParamsString: String
): ReplyResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Reply.LIST) {
        buildWithWbi {
            parameter("type", replySection.typeCode)
            parameter("oid", replySection.oid)
            parameter("mode", sort.sortCode)
            parameter("pagination_str", """{"offset":"$loadParamsString"}""")
        }
    }
    return response.body()
}