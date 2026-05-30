package tv.hsrui.network.feature.reply

sealed interface ReplyType {
    val typeCode: Int
    val oid: Long

    data class VideoReply(override val oid: Long) : ReplyType {
        override val typeCode: Int = 1
    }
}