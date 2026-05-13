package tv.hsrui.network.feature.account.myinfo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.hsrui.network.feature.user.follow.followState.FollowState
import tv.hsrui.network.feature.user.follow.followState.fetchFollowState

class MyAccountInfoManager {
    private var _info = MutableStateFlow(MyAccountInfo())
    private var _followState = MutableStateFlow(FollowState())
    val info = _info.asStateFlow()

    suspend fun loadInfo(isForce: Boolean = false) {
        try{
            if (isForce || !_info.value.isSuccess || !_followState.value.isSuccess) {
                _info.value = fetchMyAccountInfo()
                if (_info.value.isSuccess) {
                    _followState.value = fetchFollowState(mid = _info.value.mid)
                    _info.update { current ->
                        current.copy(
                            following = _followState.value.following,
                            follower = _followState.value.follower,
                        )
                    }
                }
            }
        }catch (e: Exception){
            /*TODO*/
        }
    }
}