package tv.hsrui.network.model


interface VideosResult {
    val isSuccess: Boolean
    val message: String
    val validData: ValidVideosData
}

data class ValidVideosData(
    val videosList: List<VideoCard>,
    val noMore: Boolean = false
)