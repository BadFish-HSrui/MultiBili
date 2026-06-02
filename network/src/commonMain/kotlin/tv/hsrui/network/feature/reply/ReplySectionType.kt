package tv.hsrui.network.feature.reply

sealed interface ReplySectionType {
    val typeCode: Int
    val oid: Long

    data class VideoReply(override val oid: Long) : ReplySectionType {
        override val typeCode: Int = 1
    }
}