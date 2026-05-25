package tv.hsrui.network.feature.video.actions.coin

import kotlinx.serialization.Serializable

@Serializable
data class VideoCoinResponse(
    val code: Int,
    val message: String
){
    val isSuccess = (code == 0)
}
