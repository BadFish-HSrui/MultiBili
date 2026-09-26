package tv.hsrui.bolo.player.settings

import eu.anifantakis.lib.ksafe.KSafePlain

class BoloPlayerSettings(settingsKSafe: KSafePlain) {
    val controls = PlayerControlsSettings(settingsKSafe)
    val playback = PlayerPlaybackSettings(settingsKSafe)
    val danmaku = PlayerDanmakuSettings(settingsKSafe)
    val subtitle = PlayerSubtitleSettings(settingsKSafe)
}
