package tv.hsrui.network.feature.history

import tv.hsrui.network.feature.watchLater.WatchLaterRawResponse
import tv.hsrui.network.feature.watchLater.toHistoryVideoCard

data class HistoryVideosResponse(
    val isSuccess: Boolean,
    val message: String,
    val validData: ValidHistoryVideos
) {
    constructor(raw: HistoryRawResponse,canLoadMore: Boolean): this(
        isSuccess = (raw.code == 0),
        message = raw.message,
        validData = raw.data.list
            .map { it.toHistoryVideoCard() }
            .let { list ->
                ValidHistoryVideos(
                    list = list,
                    canLoadMore = canLoadMore,
                    loadParams = HistoryLoadParams(
                        max = raw.data.cursor.max,
                        viewAt = raw.data.cursor.viewAt,
                        business = raw.data.cursor.business
                    )
                )
            }
    )

    constructor(raw: WatchLaterRawResponse): this(
        isSuccess = raw.isSuccess,
        message = raw.message,
        validData = raw.data.list
            .map { it.toHistoryVideoCard() }
            .let { list ->
                ValidHistoryVideos(
                    list = list,
                    canLoadMore = false,
                    loadParams = HistoryLoadParams()
                )
            }
    )
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
    val addTime: Long = 0,
    val watchProgress: Int = 0,
    val duration: Int = 0,
    val regionString: String = "",
    val typeString: String = ""
) {
    val isFullyWatched get() = (watchProgress == -1)
}

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
        addTime = watchTime,
        watchProgress = watchProgress,
        duration = duration,
        regionString = regionString,
        typeString = typeString
    )
}

val HistoryVideoCardExample = HistoryVideoCard(
    avid = 115327790751441,
    bvid = "BV1gDxEzHE8Z",
    title = "✨“我为你唱一曲如游丝的气息”《青衣DJ》✨/AI東 雪蓮",
    subtitle = "",
    coverUrl = "https://i2.hdslb.com/bfs/archive/7a7aa5e03fb63167e51a9d3d7a28ed2749128a45.jpg",
    upName = "东洋雪莲",
    upAvatarUrl = "https://i2.hdslb.com/bfs/face/4cbf2f66d23a324ecca8d3c07adbcafecfef829b.jpg",
    upMid = 1060544882,
    addTime = 1778616753,
    watchProgress = 21,
    duration = 153,
    regionString = "翻唱"
)