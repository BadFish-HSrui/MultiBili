package tv.hsrui.bolo.accountFeature

data class AccountFeaturesUiState(
    val showLogoutConfirmation: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutError: String? = null,
    val localCleanupError: String? = null,
    val isLoggedOut: Boolean = false,
)
