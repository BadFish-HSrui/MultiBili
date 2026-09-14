package tv.hsrui.bolo.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.login.qrcode.LoginQrCodeData
import tv.hsrui.network.login.qrcode.LoginQrCodeStatus
import tv.hsrui.network.login.qrcode.fetchLoginQrCode
import tv.hsrui.network.login.qrcode.pollLoginQrCode
import tv.hsrui.network.login.storage.LoginStorage

class LoginViewModel(private val loginStorage: LoginStorage) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private var qrCodeJob: Job? = null
    private var qrCodeVisible = false

    fun startQrCodeLogin() {
        if (qrCodeVisible || _uiState.value.isLoggedIn) return
        qrCodeVisible = true
        refreshQrCode()
    }

    fun stopQrCodeLogin() {
        qrCodeVisible = false
        qrCodeJob?.cancel()
        qrCodeJob = null
        _uiState.update { it.copy(qrCode = QrCodeLoginUiState.Idle) }
    }

    fun refreshQrCode() {
        if (!qrCodeVisible || _uiState.value.isLoggedIn) return
        qrCodeJob?.cancel()
        _uiState.update { it.copy(qrCode = QrCodeLoginUiState.Loading) }
        qrCodeJob = viewModelScope.launch {
            try {
                val completed = withTimeoutOrNull(180_000L) {
                    val data = fetchLoginQrCode()
                    currentCoroutineContext().ensureActive()
                    _uiState.update { it.copy(qrCode = QrCodeLoginUiState.Success(
                        url = data.url,
                        status = LoginQrCodeStatus.WaitingForScan,
                    )) }
                    pollQrCode(data)
                    true
                }
                if (completed == null) {
                    _uiState.update { state ->
                        val qrCode = state.qrCode
                        state.copy(qrCode = if (qrCode is QrCodeLoginUiState.Success) {
                            qrCode.copy(status = LoginQrCodeStatus.Expired)
                        } else {
                            QrCodeLoginUiState.Error("获取二维码超时，请重试")
                        })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update { it.copy(qrCode = QrCodeLoginUiState.Error(errorMessage(e))) }
            }
        }
    }

    private suspend fun pollQrCode(data: LoginQrCodeData) {
        while (true) {
            delay(2_000L)
            val result = pollLoginQrCode(data.key)
            currentCoroutineContext().ensureActive()
            if (result.status == LoginQrCodeStatus.Completed) {
                completeLogin(result.cookies)
                return
            }
            _uiState.update { it.copy(qrCode = QrCodeLoginUiState.Success(data.url, result.status)) }
            if (result.status == LoginQrCodeStatus.Expired) return
        }
    }

    private fun completeLogin(cookies: Map<String, String>) {
        if (_uiState.value.isLoggedIn) return
        val saved = loginStorage.cookies
        val deviceCookies = mapOf(
            "buvid3" to saved.buvid3,
            "buvid4" to saved.buvid4,
            "buvid_fp" to saved.buvidFp,
            "b_nut" to saved.bNut.toString(),
        )
        loginStorage.saveCookie(deviceCookies + cookies)
        _uiState.update { it.copy(isLoggedIn = true) }
        stopQrCodeLogin()
    }

    private fun errorMessage(error: Exception): String =
        if (error is IllegalStateException) error.message ?: "登录请求失败" else "网络请求失败，请重试"
}
