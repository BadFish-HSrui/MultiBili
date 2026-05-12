package tv.hsrui.network.feature.history

data class HistoryVideosResponse(
    private val raw: HistoryRawResponse,
    private val canLoadMore: Boolean
) {
    val isSuccess: Boolean get() = (raw.code == 0)
    val message: String get() = raw.message
    val validData = raw.data.list
        .map { it.toHistoryVideoCard() }
        .let { list ->
            ValidHistoryVideos(
                list = list,
                canLoadMore = canLoadMore,
                loadParams = HistoryLoadParams(
                    max = raw.data.max,
                    viewAt = raw.data.viewAt,
                    business = raw.data.business
                )
            )
        }
}

data class ValidHistoryVideos(
    val list: List<HistoryVideoCard>,
    val canLoadMore: Boolean,
    val loadParams: HistoryLoadParams
)

data class HistoryVideoCard(
    val avid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val subtitle: String = "",
    val coverUrl: String = "",
    val upName: String = "",
    val upAvatarUrl: String = "",
    val upMid: Long = 0,
    val watchTime: Long = 0,
    val watchProgress: Int = 0,
    val duration: Int = 0,
    val regionString: String = ""
)

private fun HistoryRawItem.toHistoryVideoCard(): HistoryVideoCard {
    return HistoryVideoCard(
        avid = id,
        bvid = bvid,
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        upName = upName,
        upAvatarUrl = upAvatarUrl,
        upMid = upMid,
        watchTime = watchTime,
        watchProgress = watchProgress,
        duration = duration,
        regionString = regionString
    )
}