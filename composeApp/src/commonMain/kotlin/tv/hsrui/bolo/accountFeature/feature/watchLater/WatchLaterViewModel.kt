package tv.hsrui.bolo.accountFeature.feature.watchLater

import tv.hsrui.bolo.accountFeature.feature.history.HistoryVideosViewModel
import tv.hsrui.network.feature.history.HistoryVideosResponse
import tv.hsrui.network.feature.watchLater.fetchWatchLaterVideos

class WatchLaterViewModel : HistoryVideosViewModel() {
    init {
        loadVideos()
    }

    override suspend fun firstLoad(): HistoryVideosResponse {
        return fetchWatchLaterVideos()
    }
}