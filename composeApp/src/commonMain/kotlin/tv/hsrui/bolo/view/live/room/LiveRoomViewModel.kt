package tv.hsrui.bolo.view.live.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.feature.user.info.UserCardStatsData
import tv.hsrui.network.feature.user.info.UserCardStatsResponse
import tv.hsrui.network.feature.user.info.fetchUserCardStats

class LiveRoomViewModel internal constructor(
    private val mid: Long,
    private val statsRequest: suspend (Long) -> UserCardStatsResponse = ::fetchUserCardStats,
) : ViewModel() {
    private val _stats = MutableStateFlow<UserCardStatsData?>(null)
    val stats = _stats.asStateFlow()

    init {
        if (mid > 0) {
            viewModelScope.launch {
                try {
                    val response = withTimeoutOrNull(15_000) { statsRequest(mid) }
                    ensureActive()
                    _stats.value = response?.takeIf { it.isSuccess }?.data
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    _stats.value = null
                }
            }
        }
    }
}
