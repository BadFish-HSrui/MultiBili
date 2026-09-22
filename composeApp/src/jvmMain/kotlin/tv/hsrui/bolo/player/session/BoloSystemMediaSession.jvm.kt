package tv.hsrui.bolo.player.session

internal actual fun createBoloSystemMediaSession(session: BoloPlaybackSession): BoloSystemMediaSession =
    try {
        if (System.getProperty("os.name").startsWith("Linux")) BoloMprisSession(session)
        else BoloDesktopSystemMediaNative.connect(session)
    } catch (error: LinkageError) {
        throw IllegalStateException("系统媒体原生接口不可用：${error.message}", error)
    }
