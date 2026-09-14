package tv.hsrui.bolo.login

import tv.hsrui.network.login.qrcode.LoginQrCodeStatus

data class LoginUiState(
    val qrCode: QrCodeLoginUiState = QrCodeLoginUiState.Idle,
    val isLoggedIn: Boolean = false,
)

sealed interface QrCodeLoginUiState {
    data object Idle : QrCodeLoginUiState
    data object Loading : QrCodeLoginUiState
    data class Success(
        val url: String,
        val status: LoginQrCodeStatus,
    ) : QrCodeLoginUiState
    data class Error(val message: String) : QrCodeLoginUiState
}
