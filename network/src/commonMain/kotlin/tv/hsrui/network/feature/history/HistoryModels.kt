package tv.hsrui.network.feature.history

import tv.hsrui.network.feature.watchLater.WatchLaterRawResponse
import tv.hsrui.network.feature.watchLater.toHistoryItem

data class HistoryResponse(
    val isSuccess: Boolean,
    val message: String,
    val validData: HistoryData
) {
    constructor(raw: HistoryRawResponse, canLoadMore: Boolean) : this(
        isSuccess = (raw.code == 0),
        message = raw.message,
        validData = raw.data.list
            .filter {
                it.typeString == "archive" || it.typeString == "pgc" ||
                    (it.typeString == "live" && it.id > 0)
            }
            .map { it.toHistoryItem() }
            .let { list ->
                HistoryData(
                    items = list,
                    canLoadMore = canLoadMore,
                    loadParams = HistoryLoadParams(
                        max = raw.data.cursor.max,
                        viewAt = raw.data.cursor.viewAt,
                        business = raw.data.cursor.business
                    )
                )
            }
    )

    constructor(raw: WatchLaterRawResponse) : this(
        isSuccess = raw.isSuccess,
        message = raw.message,
        validData = raw.data.list
            .map { it.toHistoryItem() }
            .let { list ->
                HistoryData(
                    items = list,
                    canLoadMore = false,
                    loadParams = HistoryLoadParams()
                )
            }
    )
}

data class HistoryData(
    val items: List<HistoryItem>,
    val canLoadMore: Boolean,
    val loadParams: HistoryLoadParams
)

data class HistoryItem(
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
    val typeString: String = "",
    val seasonId: Long = 0,
    val episodeId: Long = 0,
    val roomId: Long = 0,
    val liveStatus: Int? = null,
) {
    val isMedia: Boolean get() = typeString == "pgc"
    val isLiveRoom: Boolean get() = typeString == "live"
    val liveStatusText: String
        get() = when (liveStatus) {
            1 -> "直播中"
            0 -> "未开播"
            else -> "直播"
        }
    val recordId: Long
        get() = when {
            isLiveRoom -> roomId
            isMedia -> seasonId
            else -> avid
        }
    val recordKey: String
        get() = when {
            isLiveRoom -> "live_$roomId"
            !isMedia -> "${typeString.ifEmpty { "archive" }}_$avid"
            seasonId > 0 -> "pgc_$seasonId"
            episodeId > 0 -> "pgc_ep_$episodeId"
            else -> "pgc_av_$avid"
        }
    val isFullyWatched get() = (watchProgress == -1)
}

internal fun HistoryRawItem.toHistoryItem(): HistoryItem {
    return HistoryItem(
        avid = if (typeString == "live") 0 else id,
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
        typeString = typeString,
        seasonId = seasonId,
        episodeId = episodeId,
        roomId = if (typeString == "live") id else 0,
        liveStatus = liveStatus,
    )
}

val HistoryItemExample = HistoryItem(
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