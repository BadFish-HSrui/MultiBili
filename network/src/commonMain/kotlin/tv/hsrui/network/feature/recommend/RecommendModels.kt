package tv.hsrui.network.feature.recommend

import tv.hsrui.network.model.ValidVideosData
import tv.hsrui.network.model.VideosResult

data class RecommendResponse(val raw: RawRecommendResponse) : VideosResult {
    override val isSuccess get() = (raw.code == 0)
    override val message: String get() = raw.message
    override val validData: ValidVideosData = raw.data.items
        .filter { it.goto == "av" }
        .map { it.toVideoCard() }
        .let { list ->
            ValidVideosData(
                videosList = list,
                canLoadMore = true
            )
        }
}