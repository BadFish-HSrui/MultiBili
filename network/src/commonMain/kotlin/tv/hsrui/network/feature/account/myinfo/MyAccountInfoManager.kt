package tv.hsrui.network.feature.account.myinfo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.hsrui.network.feature.user.follow.followState.FollowState
import tv.hsrui.network.feature.user.follow.followState.fetchFollowState

class MyAccountInfoManager {
    private var _info = MutableStateFlow(MyAccountInfo())
    private var _followState = MutableStateFlow(FollowState())
    val info = _info.asStateFlow()
    private var revision = 0L

    fun clearInfo() {
        revision++
        _info.value = MyAccountInfo()
        _followState.value = FollowState()
    }

    suspend fun loadInfo(isForce: Boolean = false) {
        val requestRevision = revision
        try {
            if (isForce || !_info.value.isSuccess || !_followState.value.isSuccess) {
                val accountInfo = fetchMyAccountInfo()
                if (requestRevision != revision) return
                _info.value = accountInfo
                if (accountInfo.isSuccess) {
                    val followState = fetchFollowState(mid = accountInfo.mid)
                    if (requestRevision != revision) return
                    _followState.value = followState
                    _info.value = accountInfo.copy(
                        following = followState.following,
                        follower = followState.follower,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            /*TODO*/
        }
    }
}
