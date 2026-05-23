package tv.hsrui.network.feature.user.relation

enum class RelationAction(val title: String, val actionCode: Int) {
    Follow("关注",1),
    UnFollow("取消关注",2),
    Block("拉黑",5),
    UnBlock("取消拉黑",6),
    RemoveFans("剔出粉丝",7)
}