package tv.hsrui.network.login

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parseServerSetCookieHeader

internal suspend inline fun <reified T> readLoginResponse(response: HttpResponse): T {
    check(response.status.isSuccess()) { "登录请求失败：HTTP ${response.status.value}" }
    return response.body<LoginApiResponse<T>>().requireData()
}

internal fun readLoginCookies(response: HttpResponse): Map<String, String> {
    val cookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        .map(::parseServerSetCookieHeader)
        .associate { it.name to it.value }
    check(!cookies["SESSDATA"].isNullOrBlank() &&
        (cookies["DedeUserID"]?.toLongOrNull() ?: 0) > 0 &&
        !cookies["bili_jct"].isNullOrBlank()) {
        "登录凭据不完整，请重新登录"
    }
    return cookies
}
