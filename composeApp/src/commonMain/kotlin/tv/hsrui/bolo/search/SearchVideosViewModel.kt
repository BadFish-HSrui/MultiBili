package tv.hsrui.bolo.search

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.search.fetchSearchVideos
import tv.hsrui.network.model.VideosResult

class SearchVideosViewModel(private val keyword: String) : VideosViewModel() {
    init {
        loadVideos()
    }

    override suspend fun fetchVideos(): VideosResult {
        return fetchSearchVideos(
            keyword = keyword,
            page = pageNumber
        )
    }
}
