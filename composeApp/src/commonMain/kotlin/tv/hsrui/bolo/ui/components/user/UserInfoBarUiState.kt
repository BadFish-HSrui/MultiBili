package tv.hsrui.bolo.ui.components.user

import tv.hsrui.network.feature.user.info.UserInfoData

internal sealed interface UserInfoBarUiState {
    data object Loading : UserInfoBarUiState

    data class Success(
        val info: UserInfoData,
        val following: Long? = null,
        val follower: Long? = null,
        val likeCount: Long? = null,
        val playCount: Long? = null,
    ) : UserInfoBarUiState

    data class Error(val message: String) : UserInfoBarUiState
}
