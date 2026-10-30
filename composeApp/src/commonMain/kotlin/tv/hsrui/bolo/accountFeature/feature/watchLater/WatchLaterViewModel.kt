package tv.hsrui.bolo.accountFeature.feature.watchLater

import tv.hsrui.bolo.accountFeature.feature.history.HistoryViewModel
import tv.hsrui.network.feature.history.HistoryResponse
import tv.hsrui.network.feature.watchLater.fetchWatchLaterVideos

class WatchLaterViewModel : HistoryViewModel() {
    init {
        loadItems()
    }

    override suspend fun firstLoad(): HistoryResponse {
        return fetchWatchLaterVideos()
    }
}