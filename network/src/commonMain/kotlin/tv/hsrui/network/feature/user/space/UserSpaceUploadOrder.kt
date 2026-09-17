package tv.hsrui.network.feature.user.space

enum class UserSpaceUploadOrder(val title: String, val value: String) {
    Latest("最新发布", "pubdate"),
    MostPlayed("最多播放", "click"),
    MostFavorited("最多收藏", "stow"),
}
