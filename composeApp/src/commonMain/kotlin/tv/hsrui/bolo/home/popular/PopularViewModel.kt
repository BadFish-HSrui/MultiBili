package tv.hsrui.bolo.home.popular

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.popular.fetchPopularVideos
import tv.hsrui.network.model.VideosResult

class PopularViewModel : VideosViewModel() {
    override suspend fun fetchVideos(): VideosResult {
        return fetchPopularVideos(pn = 1,ps = 24)
    }
}