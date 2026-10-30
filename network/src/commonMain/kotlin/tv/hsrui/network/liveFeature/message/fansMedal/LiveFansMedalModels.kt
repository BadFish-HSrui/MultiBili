package tv.hsrui.network.liveFeature.message.fansMedal

import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuFansMedalData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMedalResourcesData

data class LiveFansMedalItemData(
    val medalId: Long,
    val anchorUid: Long,
    val anchorName: String,
    val roomId: Long?,
    val medal: LiveDanmakuFansMedalData,
    val isWorn: Boolean,
)

data class LiveFansMedalPageData(
    val items: List<LiveFansMedalItemData>,
    val currentPage: Int,
    val totalPages: Int,
    val totalCount: Int,
)

data class LiveFansMedalOperationData(val code: Int, val message: String) {
    val isSuccess: Boolean get() = code == 0
}

data class LiveFansMedalState(
    val items: List<LiveFansMedalItemData> = emptyList(),
    val currentMedal: LiveDanmakuFansMedalData? = null,
    val isCurrentLoaded: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val totalCount: Int = 0,
    val isLoadingList: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingCurrent: Boolean = false,
    val isChanging: Boolean = false,
    val listError: String? = null,
    val loadMoreError: String? = null,
    val currentError: String? = null,
    val operationError: String? = null,
    val operationRevision: Long = 0,
    val listRevision: Long = 0,
    val resources: LiveDanmakuMedalResourcesData = LiveDanmakuMedalResourcesData(),
) {
    val hasMore: Boolean get() = currentPage > 0 && currentPage < totalPages
}
