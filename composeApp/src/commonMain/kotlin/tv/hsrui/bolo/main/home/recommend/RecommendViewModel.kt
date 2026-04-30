package tv.hsrui.bolo.main.home.recommend

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.recommend.fetchRecommendVideos
import tv.hsrui.network.model.VideosResult

class RecommendViewModel : VideosViewModel() {
    override suspend fun fetchVideos(): VideosResult {
        return fetchRecommendVideos(freshIndex = pageNumber,ps = 24)
    }
}