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
    return parseWebID(html)
}

private fun parseWebID(html: String): String {
    val key = "\"access_id\""
    var searchIndex = 0
    while (true) {
        val keyIndex = html.indexOf(key, searchIndex)
        if (keyIndex < 0) break
        searchIndex = keyIndex + key.length
        var index = searchIndex
        while (index < html.length && html[index].isWhitespace()) index++
        if (html.getOrNull(index) != ':') continue
        index++
        while (index < html.length && html[index].isWhitespace()) index++
        if (html.getOrNull(index) != '"') continue
        val start = index++

        // 迭代定位字符串边界，避免 Native 正则对长标识逐字符递归导致栈溢出。
        while (index < html.length) {
            when (html[index]) {
                '\\' -> index += 2
                '"' -> {
                    val encoded = html.substring(start, index + 1)
                    return Json.parseToJsonElement(encoded).jsonPrimitive.content.also {
                        check(it.isNotBlank()) { "网页请求标识为空" }
                    }
                }
                else -> index++
            }
        }
        break
    }
    error("无法获取网页请求标识")
}
