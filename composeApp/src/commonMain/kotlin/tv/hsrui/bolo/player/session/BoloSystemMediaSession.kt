package tv.hsrui.bolo.player.session

enum class BoloSystemMediaPlaybackStatus { Stopped, Playing, Paused, Buffering, Ended, Error }

enum class BoloSystemMediaAction {
    Play, Pause, TogglePlayPause, SeekTo, SeekBy, Previous, Next, Stop, SetRate, SetVolume, Raise,
}

data class BoloSystemMediaMetadata(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
)

data class BoloSystemMediaState(
    val metadata: BoloSystemMediaMetadata = BoloSystemMediaMetadata(),
    val status: BoloSystemMediaPlaybackStatus = BoloSystemMediaPlaybackStatus.Stopped,
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Double = 1.0,
    val volume: Double = 1.0,
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canSeek: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val artwork: ByteArray? = null,
    val seekRevision: Long = 0L,
)

data class BoloSystemMediaCommand(
    val action: BoloSystemMediaAction,
    val mediaId: String,
    val positionMs: Long = 0L,
    val value: Double = 0.0,
)

internal interface BoloSystemMediaSession {
    fun publish(state: BoloSystemMediaState)
    fun close()
}

internal expect fun createBoloSystemMediaSession(session: BoloPlaybackSession): BoloSystemMediaSession
