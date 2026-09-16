package tv.hsrui.bolo.ui.components.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.user.follow.followState.fetchFollowState
import tv.hsrui.network.feature.user.info.fetchUserInfo
import tv.hsrui.network.feature.user.info.fetchUserUpStat

internal class UserInfoBarViewModel(private val mid: Long) : ViewModel() {
    private val _uiState = MutableStateFlow<UserInfoBarUiState>(UserInfoBarUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var requestJob: Job? = null
    private var requestVersion = 0

    fun loadUserInfo() {
        cancelLoading()
        val version = requestVersion
        if (mid <= 0) {
            _uiState.value = UserInfoBarUiState.Error("用户 UID 无效")
            return
        }
        _uiState.value = UserInfoBarUiState.Loading
        requestJob = viewModelScope.launch {
            try {
                val response = fetchUserInfo(mid)
                if (version != requestVersion) return@launch
                val info = response.data
                if (!response.isSuccess || info == null || info.mid != mid) {
                    _uiState.value = UserInfoBarUiState.Error(
                        response.message.ifBlank { "用户资料加载失败" }
                    )
                    return@launch
                }
                _uiState.value = UserInfoBarUiState.Success(info)

                launch {
                    try {
                        val relation = fetchFollowState(mid)
                        if (version == requestVersion && relation.isSuccess) {
                            _uiState.update { state ->
                                (state as? UserInfoBarUiState.Success)?.copy(
                                    following = relation.following.takeIf { it >= 0 }?.toLong(),
                                    follower = relation.follower.takeIf { it >= 0 }?.toLong(),
                                ) ?: state
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // 统计不可用时保留资料，缺失值由 UI 展示为 --。
                    }
                }
                launch {
                    try {
                        val stat = fetchUserUpStat(mid)
                        if (version == requestVersion && stat.isSuccess) {
                            _uiState.update { state ->
                                (state as? UserInfoBarUiState.Success)?.copy(
                                    likeCount = stat.data?.likeCount?.takeIf { it >= 0 },
                                    playCount = stat.data?.playCount?.takeIf { it >= 0 },
                                ) ?: state
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // 获赞与播放统计失败不影响已加载的资料和关注统计。
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == requestVersion) {
                    _uiState.value = UserInfoBarUiState.Error(e.message ?: "用户资料加载失败")
                }
            }
        }
    }

    fun cancelLoading() {
        requestVersion++
        requestJob?.cancel()
        requestJob = null
    }
}
