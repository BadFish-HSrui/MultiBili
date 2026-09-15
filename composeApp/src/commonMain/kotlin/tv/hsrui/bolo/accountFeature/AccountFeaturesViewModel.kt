package tv.hsrui.bolo.accountFeature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.account.myinfo.MyAccountInfoManager
import tv.hsrui.network.login.deleteLoginSession
import tv.hsrui.network.login.storage.LoginStorage

class AccountFeaturesViewModel(
    private val loginStorage: LoginStorage,
    private val accountInfoManager: MyAccountInfoManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountFeaturesUiState())
    val uiState = _uiState.asStateFlow()
    private var sessionDeleted = false

    fun requestLogout() {
        if (_uiState.value.isLoggingOut || _uiState.value.isLoggedOut) return
        _uiState.update { it.copy(showLogoutConfirmation = true) }
    }

    fun cancelLogout() {
        if (_uiState.value.isLoggingOut || _uiState.value.isLoggedOut) return
        _uiState.value = AccountFeaturesUiState()
    }

    fun logout() {
        val state = _uiState.value
        if (!state.showLogoutConfirmation || state.isLoggingOut || state.isLoggedOut ||
            state.logoutError != null || state.localCleanupError != null
        ) return
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            try {
                if (!sessionDeleted) {
                    deleteLoginSession()
                    sessionDeleted = true
                }
                completeLogout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(logoutError = e.message?.takeIf(String::isNotBlank) ?: "网络请求失败，请重试")
                }
            } finally {
                _uiState.update { it.copy(isLoggingOut = false) }
            }
        }
    }

    fun clearLocalCookies() {
        val state = _uiState.value
        if (!state.showLogoutConfirmation || state.isLoggingOut || state.isLoggedOut ||
            (state.logoutError == null && state.localCleanupError == null)
        ) return
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            try {
                completeLogout()
            } finally {
                _uiState.update { it.copy(isLoggingOut = false) }
            }
        }
    }

    private suspend fun completeLogout() {
        try {
            loginStorage.clearLoginCookies()
            accountInfoManager.clearInfo()
            _uiState.value = AccountFeaturesUiState(isLoggedOut = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(localCleanupError = e.message?.takeIf(String::isNotBlank) ?: "本地存储写入失败")
            }
        }
    }
}
