package tv.hsrui.network.feature.video.actions.state

import kotlinx.serialization.Serializable

@Serializable
data class VideoActionsStateResponse(
    val message: String = "",
    val isLiked: Boolean = false,
    val coinedCount: Int = 0,
    val isFavoured: Boolean = false
) {
    val isSuccess get() = (message.isEmpty())
    val isCoined get() = (coinedCount != 0)
}

@Serializable
data class VideoLikeStateResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: Int = 0
) {
    val isSuccess get() = (code == 0)
    val isLiked get() = (data == 1)
}

@Serializable
data class VideoCoinStateResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: VideoCoinData = VideoCoinData()
) {
    val isSuccess get() = (code == 0)
    val coinedCount by data::multiply

    @Serializable
    data class VideoCoinData(
        val multiply: Int = 0
    )
}

@Serializable
data class VideoFavouredStateResponse(
    val code: Int = -1,
    val message: String,
    private val data: VideoFavouredData = VideoFavouredData()
) {
    val isSuccess get() = (code == 0)
    val isFavoured by data::favoured

    @Serializable
    data class VideoFavouredData(
        val favoured: Boolean = false
    )
}