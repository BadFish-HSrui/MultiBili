package tv.hsrui.network.feature.user.follow.followState

import kotlinx.serialization.Serializable

@Serializable
data class FollowState(
    val code: Int = -1,
    val message: String = "",
    private val data: FollowStateData = FollowStateData()
) {
    val isSuccess get() = (code == 0)
    val following by data::following
    val follower by data::follower
}

@Serializable
data class FollowStateData(
    val following: Int = -1,
    val follower: Int = -1
)