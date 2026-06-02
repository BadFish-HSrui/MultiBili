package tv.hsrui.network.feature.reply

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchRepliesWith(
    replySection: ReplySectionType,
    sort: ReplySort,
    pn: Int,
    ps: Int = 20
): ReplyResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Reply.LIST) {
        parameter("type", replySection.typeCode)
        parameter("oid", replySection.oid)
        parameter("sort", sort.sortCode)
        parameter("pn", pn)
        parameter("ps", ps)
    }

    return  response.body()
}