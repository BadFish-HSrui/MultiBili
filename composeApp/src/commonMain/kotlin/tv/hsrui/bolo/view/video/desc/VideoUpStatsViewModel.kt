package tv.hsrui.bolo.view.video.desc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.network.feature.user.info.UserCardStatsData
import tv.hsrui.network.feature.user.info.fetchUserCardStats

class VideoUpStatsViewModel(private val mid: Long) : ViewModel() {
    private val _stats = MutableStateFlow<UserCardStatsData?>(null)
    val stats = _stats.asStateFlow()

    init {
        if (mid > 0) {
            viewModelScope.launch {
                try {
                    val response = withTimeoutOrNull(15_000) { fetchUserCardStats(mid) }
                    _stats.value = response?.takeIf { it.isSuccess }?.data
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _stats.value = null
                }
            }
        }
    }
}
