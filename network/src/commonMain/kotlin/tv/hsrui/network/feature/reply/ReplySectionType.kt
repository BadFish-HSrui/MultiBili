package tv.hsrui.network.feature.reply

sealed interface ReplySectionType {
    val typeCode: Int
    val oid: Long

    data class VideoReply(override val oid: Long) : ReplySectionType {
        override val typeCode: Int = 1
    }

    companion object {
        fun ReplySectionType(typeCode: Int, oid: Long): ReplySectionType? {
            return when (typeCode) {
                1 -> VideoReply(oid)
                else -> null
            }
        }
    }
}