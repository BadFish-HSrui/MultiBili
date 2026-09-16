package tv.hsrui.network.feature.user.space

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import tv.hsrui.network.model.Owner
import tv.hsrui.network.model.VideoCard

@Serializable
data class UserSpacePrivacyResponse(
    val status: Boolean = false,
    private val data: UserSpaceSettingsData? = null,
) {
    val privacy get() = if (status) data?.privacy else null
}

@Serializable
data class UserSpaceSettingsData(val privacy: UserSpacePrivacyData? = null)

@Serializable
data class UserSpacePrivacyData(
    @SerialName("likes_video") private val likesCode: Int? = null,
    @SerialName("coins_video") private val coinsCode: Int? = null,
    @SerialName("fav_video") private val favoritesCode: Int? = null,
) {
    val showsLikes get() = likesCode?.let { if (it in 0..1) it == 1 else null }
    val showsCoins get() = coinsCode?.let { if (it in 0..1) it == 1 else null }
    val showsFavorites get() = favoritesCode?.let { if (it in 0..1) it == 1 else null }
}

@Serializable
data class UserSpaceUploadsResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: UserSpaceUploadsData? = null,
) {
    val isSuccess get() = code == 0 && data?.list?.videos != null && data.page?.total != null
    val videos get() = data?.list?.videos.orEmpty().map { it.toVideoCard() }
    val total get() = data?.page?.total
    val hasMore get() = data?.page?.let { it.pageNumber.toLong() * it.pageSize < (it.total ?: 0) } == true
}

@Serializable
data class UserSpaceUploadsData(
    val list: UserSpaceUploadListData? = null,
    val page: UserSpaceUploadPageData? = null,
)

@Serializable
data class UserSpaceUploadListData(@SerialName("vlist") val videos: List<UserSpaceUploadVideoData>? = null)

@Serializable
data class UserSpaceUploadPageData(
    @SerialName("pn") val pageNumber: Int = 1,
    @SerialName("ps") val pageSize: Int = 30,
    @SerialName("count") val total: Int? = null,
)

@Serializable
data class UserSpaceUploadVideoData(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val created: Long = 0,
    val length: String = "",
    val author: String = "",
    val mid: Long = 0,
    private val play: JsonPrimitive? = null,
    @SerialName("video_review") private val danmaku: Int = -1,
    @SerialName("comment") private val replies: Int = -1,
) {
    fun toVideoCard() = VideoCard(
        avid = aid, bvid = bvid, title = title, publishDate = created,
        _cover = pic, _durationString = length, _owner = Owner(mid = mid, name = author),
        _stat = VideoCard.Stat(view = play?.intOrNull ?: -1, danmaku = danmaku, reply = replies),
    )
}

@Serializable
data class UserSpaceLikesResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: UserSpaceLikesData? = null,
) {
    val isSuccess get() = code == 0 && data?.list != null
    val isHidden get() = code == 53013
    val videos get() = data?.list.orEmpty()
}

@Serializable
data class UserSpaceLikesData(val list: List<VideoCard>? = null)

@Serializable
data class UserSpaceCoinsResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: List<VideoCard>? = null,
) {
    val isSuccess get() = code == 0 && data != null
    val isHidden get() = code == 53013
    val videos get() = data.orEmpty()
}

@Serializable
data class UserSpaceCollectionsResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: UserSpaceCollectionsData? = null,
) {
    val total get() = data?.items?.page?.total
    val isSuccess get() = code == 0 && total != null
}

@Serializable
data class UserSpaceCollectionsData(@SerialName("items_lists") val items: UserSpaceCollectionListData? = null)

@Serializable
data class UserSpaceCollectionListData(val page: UserSpaceCollectionPageData? = null)

@Serializable
data class UserSpaceCollectionPageData(val total: Int? = null)
