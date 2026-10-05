package tv.hsrui.network.feature.media.actions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaActionsStateResponse(
    private val code: Int = -1,
    @SerialName("message") private val responseMessage: String = "",
    private val data: MediaActionsData? = null,
) {
    val isSuccess: Boolean get() = code == 0 && data?.likeCount != null
    val message: String
        get() = if (code == 0 && !isSuccess) "剧集操作响应缺少统计信息" else "[$code] $responseMessage"
    val likeCount: Long? get() = data?.likeCount
    val coinCount: Long? get() = data?.coinCount
    val favoriteCount: Long? get() = data?.favoriteCount
    val hasUserCommunity: Boolean get() = data?.hasUserCommunity == true
    val isLiked: Boolean get() = data?.isLiked == true
    val coinedCount: Int get() = data?.coinedCount ?: 0
    val isFavorite: Boolean get() = data?.isFavorite == true
}

@Serializable
data class MediaActionsData(
    @SerialName("stat") private val statistics: MediaActionsStatistics? = null,
    @SerialName("user_community") private val userCommunity: MediaUserCommunity? = null,
) {
    val likeCount: Long? get() = statistics?.likeCount
    val coinCount: Long? get() = statistics?.coinCount
    val favoriteCount: Long? get() = statistics?.favoriteCount
    val hasUserCommunity: Boolean get() = userCommunity != null
    val isLiked: Boolean get() = userCommunity?.isLiked == true
    val coinedCount: Int get() = userCommunity?.coinedCount ?: 0
    val isFavorite: Boolean get() = userCommunity?.isFavorite == true
}

@Serializable
data class MediaActionsStatistics(
    @SerialName("like") val likeCount: Long = 0,
    @SerialName("coin") val coinCount: Long = 0,
    @SerialName("favorite") val favoriteCount: Long = 0,
)

@Serializable
data class MediaUserCommunity(
    @SerialName("like") private val likeCode: Int = 0,
    @SerialName("coin_number") val coinedCount: Int = 0,
    @SerialName("favorite") private val favoriteCode: Int = 0,
) {
    val isLiked: Boolean get() = likeCode == 1
    val isFavorite: Boolean get() = favoriteCode == 1
}

@Serializable
data class MediaCoinLimitResponse(
    private val code: Int = -1,
    @SerialName("message") private val responseMessage: String = "",
    private val result: MediaCoinLimitData? = null,
) {
    val isSuccess: Boolean get() = code == 0 && coinLimit != null
    val message: String
        get() = if (code == 0 && !isSuccess) "剧集投币响应缺少上限信息" else "[$code] $responseMessage"
    val coinLimit: Int? get() = result?.coinLimit
}

@Serializable
data class MediaCoinLimitData(
    @SerialName("is_original") private val originalCode: Int? = null,
) {
    val coinLimit: Int? get() = originalCode?.let { if (it == 1) 2 else 1 }
}
