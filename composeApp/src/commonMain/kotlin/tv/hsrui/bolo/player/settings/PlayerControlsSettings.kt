package tv.hsrui.bolo.player.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain

class PlayerControlsSettings(settingsKSafe: KSafePlain) {
    private var storedDanmakuEnabled by settingsKSafe(true, key = "player_controls_danmaku_enabled")
    private var currentDanmakuEnabled by mutableStateOf(storedDanmakuEnabled)

    var danmakuEnabled: Boolean
        get() = currentDanmakuEnabled
        set(value) {
            if (value == currentDanmakuEnabled) return
            storedDanmakuEnabled = value
            currentDanmakuEnabled = value
        }

    private var storedDesktopVolumePercent by settingsKSafe(
        100,
        key = "player_controls_desktop_volume_percent",
    )
    private var currentDesktopVolumePercent by mutableIntStateOf(
        storedDesktopVolumePercent.coerceIn(0, 200),
    )

    var desktopVolumePercent: Int
        get() = currentDesktopVolumePercent
        set(value) {
            val percent = value.coerceIn(0, 200)
            if (percent == currentDesktopVolumePercent) return
            storedDesktopVolumePercent = percent
            currentDesktopVolumePercent = percent
        }

    private var storedDesktopMuted by settingsKSafe(false, key = "player_controls_desktop_muted")
    private var currentDesktopMuted by mutableStateOf(storedDesktopMuted)

    var desktopMuted: Boolean
        get() = currentDesktopMuted
        set(value) {
            if (value == currentDesktopMuted) return
            storedDesktopMuted = value
            currentDesktopMuted = value
        }
}
