package tv.hsrui.bolo.main.media

enum class MediaTab(val title: String, val seasonType: Int) {
    Bangumi("番剧", 1),
    Movie("电影", 2),
    ChineseAnimation("国创", 4),
    Documentary("纪录片", 3),
    TvSeries("电视剧", 5),
    Variety("综艺", 7),
}
