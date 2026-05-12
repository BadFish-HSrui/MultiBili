package tv.hsrui.network.feature.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.feature.utils.toHttpsUrl

@Serializable
data class HistoryRawResponse(
    val code: Int = -1,
    val message: String = "",
    val data: HistoryRawData = HistoryRawData()
)

@Serializable
data class HistoryRawData(
    @SerialName("view_at") val viewAt: Long = 0,
    val max: Long = 0,
    val business: String = "",
    val list: List<HistoryRawItem> = emptyList()
)

@Serializable
data class HistoryRawItem(
    @SerialName("title") val title: String = "",
    @SerialName("long_title") private val _longTitle: String = "",
    @SerialName("cover") private val _coverUrl: String = "",
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

    val typeString by info::typeString
    val coverUrl by lazy { _coverUrl.toHttpsUrl() }
    val upAvatarUrl by lazy { _upAvatarUrl.toHttpsUrl() }
    val subtitle by lazy { _showTitle.ifEmpty { _longTitle } }

}

@Serializable
data class HistoryRawItemInfo(
    @SerialName("oid") val id: Long = 0,
    @SerialName("bvid") val bvid: String = "",
    @SerialName("business") val typeString: String = ""
)

data class HistoryLoadParams(
    val max: Long = 0,
    val viewAt: Long = 0,
    val business: String = ""
)