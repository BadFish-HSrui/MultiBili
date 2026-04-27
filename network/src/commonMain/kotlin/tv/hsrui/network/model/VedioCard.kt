package tv.hsrui.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoCard(
    @SerialName("aid")      val avid: Long,
    @SerialName("bvid")     val bvid: String,
    @SerialName("pic")      val coverUrl: String,
    @SerialName("title")    val title: String,
    @SerialName("desc")     val description: String,
    @SerialName("pubdate")  val publishDate: Long,
    @SerialName("stat")     private val stat: Stat,
    @SerialName("owner")    private val owner: Owner
) {

    val viewCount get() = stat.view
    val likeCount get() = stat.like
    val upName get() = owner.name
    val upAvatarUrl get() = owner.face

    @Serializable
    data class Stat(
        val view: Int = -1,
        val like: Int = -1
    )
    @Serializable
    data class Owner(
        val name: String,
        val face: String
    )
}