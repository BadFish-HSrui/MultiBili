package tv.hsrui.bolo.main.home.popular

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.popular.fetchPopularVideos
import tv.hsrui.network.model.VideosResult

class PopularViewModel : VideosViewModel() {
    init { loadVideos() }

    override suspend fun fetchVideos(): VideosResult {
        return fetchPopularVideos(pn = pageNumber,ps = 24)
    }
}