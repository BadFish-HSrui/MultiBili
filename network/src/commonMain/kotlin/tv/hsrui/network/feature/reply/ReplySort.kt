package tv.hsrui.network.feature.reply

enum class ReplySort(val sortTitle: String, val sortCode: Int) {
//    Trending("热度+时间", 1), //这个排序似乎和按时间没有区别
    Latest("按时间", 2),
    Popular("按热度", 3)
}