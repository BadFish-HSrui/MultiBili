package tv.hsrui.bolo.livePlayer.base

data class LivePlayerState(
    val localReplayEnabled: Boolean = false,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPlaybackSuspended: Boolean = false,
    val isEnded: Boolean = false,
    val isBroadcastEnded: Boolean = false,
    val error: String? = null,
    val pendingSeekPositionMs: Long? = null,
) {
    val isSeeking: Boolean get() = pendingSeekPositionMs != null
}
