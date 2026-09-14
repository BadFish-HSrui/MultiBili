package tv.hsrui.network.login.qrcode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginQrCodeData(
    val url: String = "",
    @SerialName("qrcode_key") val key: String = "",
)

enum class LoginQrCodeStatus {
    WaitingForScan,
    WaitingForConfirmation,
    Expired,
    Completed,
}

@Serializable
internal data class RawLoginQrCodePollData(
    private val code: Int = -1,
    private val message: String = "",
) {
    val status: LoginQrCodeStatus
        get() = when (code) {
            86101 -> LoginQrCodeStatus.WaitingForScan
            86090 -> LoginQrCodeStatus.WaitingForConfirmation
            86038 -> LoginQrCodeStatus.Expired
            0 -> LoginQrCodeStatus.Completed
            else -> error(message.takeIf { it.isNotBlank() } ?: "未知扫码状态（code=$code）")
        }
}

data class LoginQrCodePollData(
    val status: LoginQrCodeStatus,
    val cookies: Map<String, String> = emptyMap(),
)
