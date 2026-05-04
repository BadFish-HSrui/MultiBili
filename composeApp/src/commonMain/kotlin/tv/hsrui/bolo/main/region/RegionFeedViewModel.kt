package tv.hsrui.bolo.main.region

import tv.hsrui.bolo.ui.common.videosPage.VideosViewModel
import tv.hsrui.network.feature.region.Regions
import tv.hsrui.network.feature.region.fetchRegionFeed
import tv.hsrui.network.model.VideosResult

class RegionFeedViewModel(private val region: Regions) : VideosViewModel() {
    init { loadVideos() }

    override suspend fun fetchVideos(): VideosResult {
        return fetchRegionFeed(fromRegion = region.tid, displayId = pageNumber, requestCnt = 20)
    }
}