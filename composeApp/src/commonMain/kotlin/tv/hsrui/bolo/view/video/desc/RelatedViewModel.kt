package tv.hsrui.bolo.view.video.desc

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.related.video.fetchRelatedVideosFor
import tv.hsrui.network.model.VideosResult

class RelatedViewModel(private val avid:Long) : VideosViewModel() {
    init {
        loadVideos()
    }

    override suspend fun fetchVideos(): VideosResult {
        return fetchRelatedVideosFor(avid)
    }
}