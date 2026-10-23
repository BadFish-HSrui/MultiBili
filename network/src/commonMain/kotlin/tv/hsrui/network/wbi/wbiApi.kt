package tv.hsrui.network.wbi

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.constant.LiveApiUrls

suspend fun fetchWbiString(): String {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.WBI).body<NavBarResponse>()

    if (response.isSuccess) return deriveWbiKey(response.wbiImgKey, response.wbiSubKey)
    else error("[${response.code}]: ${response.message}")
}

suspend fun fetchWebID(): String {
    val html = ApiClient.httpClient.get(LiveApiUrls.AREA_PAGE) {
        parameter("parentAreaId", 9)
        parameter("areaId", 0)
    }.bodyAsText()
    val encoded = Regex("\"access_id\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")")
        .find(html)?.groupValues?.get(1) ?: error("无法获取网页请求标识")
    return Json.parseToJsonElement(encoded).jsonPrimitive.content.also {
        check(it.isNotBlank()) { "网页请求标识为空" }
    }
}