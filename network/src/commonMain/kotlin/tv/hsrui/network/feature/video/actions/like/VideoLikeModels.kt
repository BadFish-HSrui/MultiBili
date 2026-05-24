package tv.hsrui.network.feature.video.actions.like

import kotlinx.serialization.Serializable

@Serializable
data class VideoLikeResponse(
    val code: Int = -1,
    val message: String = ""
){
    val isSuccess get() = (code == 0)
}
