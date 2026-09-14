package tv.hsrui.network.login.qrcode

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.PassportUrls
import tv.hsrui.network.login.readLoginCookies
import tv.hsrui.network.login.readLoginResponse

suspend fun fetchLoginQrCode(): LoginQrCodeData {
    val response = ApiClient.httpClient.get(PassportUrls.BASE + PassportUrls.QR_CODE_GENERATE) {
        parameter("source", "main_web")
    }
    val data = readLoginResponse<LoginQrCodeData>(response)
    check(data.url.isNotBlank() && data.key.isNotBlank()) { "获取登录二维码失败" }
    return data
}

suspend fun pollLoginQrCode(key: String): LoginQrCodePollData {
    val response = ApiClient.httpClient.get(PassportUrls.BASE + PassportUrls.QR_CODE_POLL) {
        parameter("qrcode_key", key)
        parameter("source", "main_web")
    }
    val status = readLoginResponse<RawLoginQrCodePollData>(response).status
    return LoginQrCodePollData(
        status = status,
        cookies = if (status == LoginQrCodeStatus.Completed) readLoginCookies(response) else emptyMap(),
    )
}
