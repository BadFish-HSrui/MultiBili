package tv.hsrui.network.liveFeature.message

import kotlinx.serialization.json.JsonObject

data class LiveMessageInfoData(
    val token: String,
    val urls: List<String>,
    val userId: Long,
    val buvid: String,
) {
    override fun toString(): String = "LiveMessageInfoData(nodes=${urls.size})"
}

data class LiveMessageData(val roomId: Long, val command: String, val payload: JsonObject, val generation: Long)

enum class LiveMessageConnectionStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING, CLOSED }

data class LiveMessageConnectionState(
    val status: LiveMessageConnectionStatus = LiveMessageConnectionStatus.IDLE,
    val roomId: Long? = null,
    val generation: Long = 0,
    val connectionId: Long = 0,
)

data class LiveRoomStatusData(
    val roomId: Long,
    val isLive: Boolean,
    val revision: Long,
    val generation: Long,
    val liveStartTimeEpochSeconds: Long? = null,
    val broadcastRevision: Long = 0,
)
