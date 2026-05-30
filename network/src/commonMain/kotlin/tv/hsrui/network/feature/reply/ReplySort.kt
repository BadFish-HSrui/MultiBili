package tv.hsrui.network.feature.reply

enum class ReplySort(val sortTitle: String, val sortCode: Int) {
    Time("按时间", 0),
    Like("按点赞", 1),
    Reply("按回复", 2)
}