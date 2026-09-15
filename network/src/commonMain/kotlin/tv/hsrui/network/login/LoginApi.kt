package tv.hsrui.network.login

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.parseServerSetCookieHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.PassportUrls
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.BaseResponse

suspend fun deleteLoginSession() {
    val loginStorage: LoginStorage = getKoin().get()
    val completed = withTimeoutOrNull(15_000L) {
        val response = ApiClient.httpClient.post(PassportUrls.BASE + PassportUrls.LOGOUT) {
            setBody(FormDataContent(Parameters.build {
                append("biliCSRF", loginStorage.cookies.csrf)
            }))
        }
        check(response.status.isSuccess()) { "注销请求失败：HTTP ${response.status.value}" }
        val result = try {
            response.body<BaseResponse>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("无法解析注销接口响应，登录凭证可能已失效", e)
        }
        check(result.isSuccess) {
            result.message.takeUnless { it.isBlank() || it == "0" }
                ?: "注销请求失败（code=${result.code}）"
        }
        true
    }
    check(completed == true) { "注销请求超时，请重试" }
}

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
