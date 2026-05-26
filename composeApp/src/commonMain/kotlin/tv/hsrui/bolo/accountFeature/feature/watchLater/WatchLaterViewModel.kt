package tv.hsrui.bolo.accountFeature.feature.watchLater

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.watchLater.fetchWatchLaterVideos
import tv.hsrui.network.model.VideosResult

class WatchLaterViewModel : VideosViewModel() {
    init {
        loadVideos()
    }

    override suspend fun fetchVideos(): VideosResult {
        return fetchWatchLaterVideos()
    }
}