package tv.hsrui.network.feature.user.relation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RelationResponse(
    val code: Int = -1,
    val message: String = "",
    val data: RelationDate = RelationDate()
) {
    val isSuccess get() = (code == 0)
}

@Serializable
data class RelationDate(
    @SerialName("relation") val to: RelationState = RelationState()
)

@Serializable
data class RelationState(
    @SerialName("attribute") val attribute: Int = 0,
    @SerialName("special") private val _special: Int = 0
) {
    val relationString
        get() = when (attribute) {
            0 -> "未关注"
            2 -> if (_special == 1) "特别关注" else "已关注"
            6 -> "已互关"
            128 -> "已拉黑"
            else -> "未关注"
        }
    val isFollowing get() = (attribute == 2 || attribute == 6)
}

