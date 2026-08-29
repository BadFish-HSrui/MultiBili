package tv.hsrui.network.feature.search

import tv.hsrui.network.model.ValidVideosData
import tv.hsrui.network.model.VideosResult

data class SearchVideosResponse(val raw: RawSearchVideosResponse) : VideosResult {
    override val isSuccess: Boolean get() = raw.code == 0 && raw.data?.numResults != null
    override val message: String
        get() = if (raw.code == 0 && raw.data?.numResults == null) {
            "搜索响应缺少结果数据"
        } else {
            raw.message
        }
    override val validData: ValidVideosData = ValidVideosData(
        videosList = raw.data?.videos.orEmpty().map { it.toVideoCard() },
        canLoadMore = raw.data?.let { it.page < it.pageCount } ?: false
    )
}
