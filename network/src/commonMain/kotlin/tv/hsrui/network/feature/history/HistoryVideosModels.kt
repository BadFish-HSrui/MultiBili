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
                    max = raw.data.cursor.max,
                    viewAt = raw.data.cursor.viewAt,
                    business = raw.data.cursor.business
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
    val regionString: String = "",
    val typeString: String = ""
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
    watchTime = 1778616753,
    watchProgress = 21,
    duration = 153,
    regionString = "翻唱"
)