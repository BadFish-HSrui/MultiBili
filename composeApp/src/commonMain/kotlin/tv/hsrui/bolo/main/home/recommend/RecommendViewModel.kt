package tv.hsrui.bolo.main.home.recommend

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.recommend.fetchRecommendVideos
import tv.hsrui.network.model.VideosResult
import kotlin.random.Random

class RecommendViewModel : VideosViewModel() {
    override suspend fun fetchVideos(): VideosResult {
        return fetchRecommendVideos(freshIndex = pageNumber,ps = 24)
    }

    override fun startLoading() {
        pageNumber = Random.nextInt(114514)
        loadVideos()
    }

    override fun refreshVideos() {
        isRefreshing = true
        loadVideos()
        isRefreshing = false
    }
}