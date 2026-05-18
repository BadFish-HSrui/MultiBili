package tv.hsrui.bolo.main.home.following

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.dynamic.fetchFollowingVideos
import tv.hsrui.network.model.VideosResult

class FollowingVideosViewModel : VideosViewModel() {
    init {
        loadVideos()
    }
    var offset: String = ""

    override fun resetPageNumber() {
        offset = ""
        super.resetPageNumber()
    }

    override suspend fun fetchVideos(): VideosResult {
        val result = fetchFollowingVideos(pn = pageNumber, offset = offset)
        offset = result.offset
        return result
    }
}