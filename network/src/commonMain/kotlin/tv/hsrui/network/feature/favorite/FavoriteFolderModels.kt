package tv.hsrui.network.feature.favorite

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class FavoriteFolderListResponse(
    private val code: Int = -1,
    val message: String = "",
    private val data: FavoriteFolderListData? = null
) {
    val isSuccess: Boolean get() = (code == 0)
    val folders: List<FavoriteFolderInfoData> get() = data?.folders.orEmpty()

    internal fun withFolders(folders: List<FavoriteFolderInfoData>): FavoriteFolderListResponse =
        copy(data = FavoriteFolderListData(folders))
}

@Serializable
data class FavoriteFolderListData(
    @SerialName("list") private val list: List<FavoriteFolderInfoData>? = null
) {
    val folders: List<FavoriteFolderInfoData> get() = list.orEmpty()
}

@Serializable
data class FavoriteFolderInfoResponse(
    private val code: Int = -1,
    val message: String = "",
    private val data: FavoriteFolderInfoData? = null
) {
    val isSuccess: Boolean get() = (code == 0)
    val folder: FavoriteFolderInfoData? get() = data
}

@Serializable
data class FavoriteFolderContentResponse(
    private val code: Int = -1,
    val message: String = "",
    private val data: FavoriteFolderContentData? = null
) {
    val isSuccess: Boolean get() = (code == 0)
    val folderInfo: FavoriteFolderInfoData? get() = data?.info
    val videos: List<FavoriteVideoCard> get() = data?.videos.orEmpty()
    val hasMore: Boolean get() = data?.hasMore == true
}

@Serializable
data class FavoriteFolderInfoData(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    @SerialName("attr") private val attributeBits: Int = 0,
    val title: String = "",
    @SerialName("cover") private val _cover: String = "",
    @SerialName("media_count") val mediaCount: Int = 0
) {
    val coverUrl get() = _cover.toHttpsUrl()
    val isPrivate: Boolean get() = (attributeBits and 1) != 0
    val isDefault: Boolean get() = (attributeBits and 2) == 0
}

@Serializable
data class FavoriteFolderContentData(
    val info: FavoriteFolderInfoData? = null,
    @SerialName("medias") private val medias: List<FavoriteVideoCard>? = null,
    @SerialName("has_more") val hasMore: Boolean = false
) {
    val videos: List<FavoriteVideoCard> get() = medias.orEmpty().filter { it.isVideo }
}
