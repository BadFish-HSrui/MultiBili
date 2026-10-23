package tv.hsrui.bolo.livePlayer.base

data class LivePlayerState(
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPlaybackSuspended: Boolean = false,
    val isEnded: Boolean = false,
    val error: String? = null,
)
