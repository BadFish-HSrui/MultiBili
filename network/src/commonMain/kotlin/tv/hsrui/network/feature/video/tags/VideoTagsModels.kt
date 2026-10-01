package tv.hsrui.network.feature.video.tags

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoTagsResponse(
    private val code: Int = -1,
    @SerialName("data") private val rawTags: List<VideoTagData>? = null,
) {
    val isSuccess: Boolean get() = code == 0
    val tags: List<String>
        get() = if (isSuccess) {
            rawTags.orEmpty().filter { it.isSearchTag && it.name.isNotBlank() }.map { it.name }
        } else emptyList()
}

@Serializable
data class VideoTagData(
    @SerialName("tag_name") val name: String = "",
    @SerialName("tag_type") private val tagType: String = "",
) {
    val isSearchTag: Boolean get() = tagType == "old_channel"
}
