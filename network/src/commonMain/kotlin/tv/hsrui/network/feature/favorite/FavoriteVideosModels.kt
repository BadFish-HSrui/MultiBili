package tv.hsrui.network.feature.favorite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.Owner
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class FavoriteVideoCard(
    @SerialName("id") val avid: Long = 0,
    @SerialName("type") private val typeCode: Int = 0,
    val bvid: String = "",
    val title: String = "",
    @SerialName("cover") private val cover: String = "",
    val duration: Int = 0,
    @SerialName("upper") private val owner: Owner? = null,
    @SerialName("attr") private val attributeCode: Int = 0,
    @SerialName("fav_time") val favoriteTime: Long = 0
) {
    val isVideo: Boolean get() = (typeCode == 2)
    val isAvailable: Boolean get() = (attributeCode == 0)
    val upName get() = owner?.name.orEmpty()
    val coverUrl get() = cover.toHttpsUrl()
    val upAvatarUrl get() = owner?.face.orEmpty().toHttpsUrl()
}
