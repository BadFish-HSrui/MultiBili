package tv.hsrui.network.feature.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.ValidVideosData
import tv.hsrui.network.model.VideosResult
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class SearchSuggestionsResponse(
    private val code: Int = -1,
    private val data: SearchSuggestionsData? = null,
) {
    val isSuccess: Boolean get() = code == 3 || (code == 0 && data?.isSuccess == true)
    val keywords: List<String> get() = if (code == 0) data?.keywords.orEmpty() else emptyList()
}

@Serializable
data class SearchSuggestionsData(
    private val code: Int = -1,
    private val result: SearchSuggestionsResult? = null,
) {
    val isSuccess: Boolean get() = code == 3 || (code == 0 && result != null)
    val keywords: List<String> get() = if (code == 0) result?.keywords.orEmpty() else emptyList()
}

@Serializable
data class SearchSuggestionsResult(
    @SerialName("tag") private val suggestions: List<SearchSuggestionData> = emptyList(),
) {
    val keywords: List<String> get() = suggestions.map { it.keyword.trim() }.filter { it.isNotEmpty() }
}

@Serializable
data class SearchSuggestionData(
    @SerialName("value") val keyword: String = "",
)

enum class SearchCategory(val title: String, val apiValue: String) {
    Video("视频", "video"),
    Bangumi("番剧", "media_bangumi"),
    Film("影视", "media_ft"),
    User("用户", "bili_user"),
}

enum class SearchVideoOrder(val title: String, val apiValue: String) {
    Comprehensive("综合排序", "totalrank"),
    MostPlayed("最多播放", "click"),
    LatestPublished("最新发布", "pubdate"),
    MostDanmaku("最多弹幕", "dm"),
    MostFavorited("最多收藏", "stow"),
}

enum class SearchUserOrder(val title: String, val apiOrder: String?, val sortDirection: Int?) {
    Default("默认排序", null, null),
    FansDescending("粉丝最多", "fans", 0),
    FansAscending("粉丝最少", "fans", 1),
    LevelDescending("等级最高", "level", 0),
    LevelAscending("等级最低", "level", 1),
}

data class SearchVideosResponse(val raw: RawSearchVideosResponse) : VideosResult {
    private val hasValidData: Boolean
        get() = raw.data?.let {
            (it.numResults ?: -1) >= 0 && it.page > 0 && it.pageCount >= 0 &&
                (it.numResults == 0 || it.videos != null)
        } == true
    override val isSuccess: Boolean get() = raw.code == 0 && hasValidData
    override val message: String
        get() = if (raw.code == 0 && !hasValidData) {
            "搜索响应缺少结果数据"
        } else {
            raw.message
        }
    override val validData: ValidVideosData = ValidVideosData(
        videosList = raw.data?.videos.orEmpty().map { it.toVideoCard() },
        canLoadMore = raw.data?.let { it.page < it.pageCount } ?: false
    )
    val pageNumber: Int get() = raw.data?.page ?: 0
}

data class SearchMediaResponse(private val raw: RawSearchMediaResponse) {
    private val hasValidData: Boolean
        get() = raw.data?.let {
            (it.numResults ?: -1) >= 0 && it.page > 0 && it.pageCount >= 0 &&
                (it.numResults == 0 || it.media != null)
        } == true
    val isSuccess: Boolean get() = raw.code == 0 && hasValidData
    val message: String
        get() = if (raw.code == 0 && !hasValidData) "搜索响应缺少结果数据" else raw.message
    val media get() = raw.data?.media.orEmpty().filter { it.seasonId > 0 }.map { it.toMediaCard() }
    val pageNumber: Int get() = raw.data?.page ?: 0
    val hasMore: Boolean get() = raw.data?.let { it.page < it.pageCount } == true
}

data class SearchUsersResponse(private val raw: RawSearchUsersResponse) {
    private val hasValidData: Boolean
        get() = raw.data?.let {
            (it.numResults ?: -1) >= 0 && it.page > 0 && it.pageCount >= 0 &&
                (it.numResults == 0 || it.users != null)
        } == true
    val isSuccess: Boolean get() = raw.code == 0 && hasValidData
    val message: String
        get() = if (raw.code == 0 && !hasValidData) "搜索响应缺少结果数据" else raw.message
    val users: List<SearchUserData> get() = raw.data?.users.orEmpty().filter { it.mid > 0 }
    val pageNumber: Int get() = raw.data?.page ?: 0
    val hasMore: Boolean get() = raw.data?.let { it.page < it.pageCount } == true
}

@Serializable
data class SearchUserData(
    val mid: Long = 0,
    @SerialName("uname") private val rawName: String = "",
    @SerialName("usign") private val rawSign: String = "",
    @SerialName("upic") private val face: String = "",
    val level: Int = 0,
    @SerialName("fans") val followerCount: Long? = null,
    @SerialName("videos") val videoCount: Long? = null,
) {
    val name: String get() = rawName.replace("<em class=\"keyword\">", "").replace("</em>", "")
    val sign: String get() = rawSign.replace("<em class=\"keyword\">", "").replace("</em>", "")
    val avatarUrl: String get() = face.toHttpsUrl()
    val levelString: String get() = "Lv.$level"
}
