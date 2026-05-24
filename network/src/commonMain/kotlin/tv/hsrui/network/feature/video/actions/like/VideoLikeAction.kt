package tv.hsrui.network.feature.video.actions.like

enum class VideoLikeAction(val title: String, val actionCode: Int) {
    Like("点赞",1),
    UnLike("取消点赞",2)
}