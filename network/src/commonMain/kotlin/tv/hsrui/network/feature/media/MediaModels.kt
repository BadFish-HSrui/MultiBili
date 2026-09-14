package tv.hsrui.network.feature.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.MediaCard

@Serializable
data class MediaIndexResponse(
    private val code: Int = -1,
    @SerialName("message") private val responseMessage: String = "",
    private val data: MediaIndexData? = null,
) {
    val isSuccess: Boolean get() = code == 0 && data != null
    val message: String
        get() = if (code == 0 && data == null) "影视番剧响应缺少列表数据" else responseMessage
    val media: List<MediaCard> get() = data?.media.orEmpty()
    val hasMore: Boolean get() = data?.hasMore == true
}

@Serializable
data class MediaIndexData(
    @SerialName("list") private val list: List<MediaCard>? = null,
    @SerialName("has_next") private val hasNext: Int = 0,
) {
    val media: List<MediaCard> get() = list.orEmpty()
    val hasMore: Boolean get() = hasNext == 1
}
