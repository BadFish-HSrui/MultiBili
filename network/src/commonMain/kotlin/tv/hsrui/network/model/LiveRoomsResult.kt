package tv.hsrui.network.model

interface LiveRoomsResult {
    val code: Int
    val message: String
    val isSuccess: Boolean
    val rooms: List<LiveCard>
    val hasMore: Boolean
}
