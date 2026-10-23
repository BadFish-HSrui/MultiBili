package tv.hsrui.bolo.main.live.recommend

import tv.hsrui.bolo.ui.common.liveRoomsPage.LiveRoomsViewModel
import tv.hsrui.network.liveFeature.recommend.fetchLiveRecommend

class LiveRecommendViewModel : LiveRoomsViewModel() {
    override val loadErrorMessage = "直播推荐加载失败"

    init {
        loadRooms()
    }

    override suspend fun fetchRooms(page: Int) = fetchLiveRecommend(page = page, pageSize = 30)
}
