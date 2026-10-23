package tv.hsrui.bolo.main.live.area

import tv.hsrui.bolo.ui.common.liveRoomsPage.LiveRoomsViewModel
import tv.hsrui.network.liveFeature.area.fetchLiveAreaRooms

class LiveAreaRoomsViewModel(
    private val parentAreaId: Int,
    private val areaId: Int,
) : LiveRoomsViewModel() {
    override val loadErrorMessage = "分区直播加载失败"

    override suspend fun fetchRooms(page: Int) = fetchLiveAreaRooms(parentAreaId, areaId, page)
}
