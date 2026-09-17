package tv.hsrui.network.feature.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class HistoryRawResponse(
    val code: Int = -1,
    val message: String = "",
    val data: HistoryRawData = HistoryRawData()
)

@Serializable
data class HistorySearchResponse(
    private val code: Int = -1,
    val message: String = "",
    private val data: HistorySearchData? = null,
) {
    val isSuccess: Boolean get() = code == 0
    val videos: List<HistoryVideoCard> get() = data?.videos.orEmpty()
    val hasMore: Boolean get() = data?.hasMore == true
}

@Serializable
data class HistorySearchData(
    @SerialName("list") private val items: List<HistoryRawItem>? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
) {
    val videos: List<HistoryVideoCard> get() = items.orEmpty()
        .filter { (it.typeString == "archive" && it.id > 0 && it.bvid.isNotBlank()) || it.typeString == "pgc" }
        .map { it.toHistoryVideoCard() }
}

@Serializable
data class HistoryRawData(
    val cursor: HistoryRowCursor = HistoryRowCursor(),
    val list: List<HistoryRawItem> = emptyList()
)

@Serializable
data class HistoryRowCursor(
    @SerialName("view_at") val viewAt: Long = 0,
    val max: Long = 0,
    val business: String = ""
)

@Serializable
data class HistoryRawItem(
    @SerialName("title") val title: String = "",
    @SerialName("long_title") private val _longTitle: String = "",
    @SerialName("cover") private val _coverUrl: String = "",
    @SerialName("kid") private val targetId: Long = 0,
    @SerialName("uri") private val uri: String = "",
    @SerialName("history") private val info: HistoryRawItemInfo = HistoryRawItemInfo(),
    @SerialName("author_name") val upName: String = "",
    @SerialName("author_face") private val _upAvatarUrl: String = "",
    @SerialName("author_mid") val upMid: Long = 0,
    @SerialName("view_at") val watchTime: Long = 0,
    @SerialName("progress") val watchProgress: Int = 0,
    @SerialName("show_title") private val _showTitle: String = "",
    @SerialName("duration") val duration: Int = 0,
    @SerialName("tag_name") val regionString: String = ""

) {
    val id by info::id
    val bvid by info::bvid
    val episodeId by info::episodeId
    val seasonId: Long
        get() = if (typeString == "pgc") {
            targetId.takeIf { it > 0 }
                ?: Regex("/bangumi/play/ss([0-9]+)(?:[/?#]|$)").find(uri)
                    ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        } else 0L

    val typeString by info::typeString
    val coverUrl by lazy { _coverUrl.toHttpsUrl() }
    val upAvatarUrl by lazy { _upAvatarUrl.toHttpsUrl() }
    val subtitle by lazy { _showTitle.ifEmpty { _longTitle } }

}

@Serializable
data class HistoryRawItemInfo(
    @SerialName("oid") val id: Long = 0,
    @SerialName("epid") val episodeId: Long = 0,
    @SerialName("bvid") val bvid: String = "",
    @SerialName("business") val typeString: String = ""
)

data class HistoryLoadParams(
    val max: Long = 0,
    val viewAt: Long = 0,
    val business: String = ""
)