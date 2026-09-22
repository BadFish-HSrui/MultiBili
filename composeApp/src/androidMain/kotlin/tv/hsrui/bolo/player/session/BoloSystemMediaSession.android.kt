package tv.hsrui.bolo.player.session

import androidx.media3.common.util.UnstableApi

@androidx.annotation.OptIn(UnstableApi::class)
internal actual fun createBoloSystemMediaSession(session: BoloPlaybackSession): BoloSystemMediaSession {
    BoloMediaSessionService.attach(session)
    return object : BoloSystemMediaSession {
        override fun publish(state: BoloSystemMediaState) = BoloMediaSessionService.publish(session, state)
        override fun close() = BoloMediaSessionService.detach(session)
    }
}
