package tv.hsrui.bolo.main.live.following

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.ui.common.liveRoomsPage.LiveRoomsViewModel
import tv.hsrui.network.liveFeature.following.LiveFollowingResponse
import tv.hsrui.network.liveFeature.following.fetchLiveFollowing
import tv.hsrui.network.login.storage.LoginStorage

class LiveFollowingViewModel(private val accountMid: Long) : LiveRoomsViewModel() {
    private val loginStorage = getKoin().get<LoginStorage>()
    override val loadErrorMessage = "关注直播加载失败"

    init {
        viewModelScope.launch {
            loginStorage.currentUserMidFlow.collect { mid ->
                if (mid != accountMid || mid == 0L) resetRooms("账号未登录")
            }
        }
    }

    override suspend fun fetchRooms(page: Int): LiveFollowingResponse {
        check(loginStorage.isLoggedIn && accountMid > 0) { "账号未登录" }
        if (loginStorage.cookies.dedeUserID != accountMid) throw CancellationException("账号已切换")
        val result = fetchLiveFollowing(page)
        if (!loginStorage.isLoggedIn || loginStorage.cookies.dedeUserID != accountMid) {
            throw CancellationException("账号已切换")
        }
        return result
    }
}