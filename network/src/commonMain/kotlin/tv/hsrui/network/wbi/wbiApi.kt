package tv.hsrui.network.wbi

import io.ktor.client.call.body
import io.ktor.client.request.get
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls

suspend fun fetchWbiString(): String {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.WBI).body<NavBarResponse>()

    if (response.isSuccess) return deriveWbiKey(response.wbiImgKey, response.wbiSubKey)
    else error("[${response.code}]: ${response.message}")
}