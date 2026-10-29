package tv.hsrui.bolo.boloSetting.setting.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import tv.hsrui.bolo.player.settings.PlayerShortcutAction
import tv.hsrui.bolo.player.settings.PlayerShortcutSettings
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoCodec
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality
import kotlin.math.roundToInt

class LiveSettings(settingsKSafe: KSafePlain) {
    val danmaku = LiveDanmakuSettings(settingsKSafe)
    val shortcuts = PlayerShortcutSettings(
        settingsKSafe,
        storageKey = "bolo_live_shortcuts_desktop_bindings",
        actions = listOf(
            PlayerShortcutAction.TogglePlayback,
            PlayerShortcutAction.VolumeUp,
            PlayerShortcutAction.VolumeDown,
            PlayerShortcutAction.ToggleMuted,
            PlayerShortcutAction.ToggleDanmaku,
            PlayerShortcutAction.ToggleFullscreen,
        ),
    )

    private var storedAutoFullscreenOnRotateEnabled by settingsKSafe(true, key = "bolo_live_auto_fullscreen_on_rotate_enabled")
    private var currentAutoFullscreenOnRotateEnabled by mutableStateOf(storedAutoFullscreenOnRotateEnabled)
    var autoFullscreenOnRotateEnabled: Boolean
        get() = currentAutoFullscreenOnRotateEnabled
        set(value) {
            if (value == currentAutoFullscreenOnRotateEnabled) return
            storedAutoFullscreenOnRotateEnabled = value
            currentAutoFullscreenOnRotateEnabled = value
        }

    private var storedBrightnessGestureEnabled by settingsKSafe(true, key = "bolo_live_brightness_gesture_enabled")
    private var currentBrightnessGestureEnabled by mutableStateOf(storedBrightnessGestureEnabled)
    var brightnessGestureEnabled: Boolean
        get() = currentBrightnessGestureEnabled
        set(value) {
            if (value == currentBrightnessGestureEnabled) return
            storedBrightnessGestureEnabled = value
            currentBrightnessGestureEnabled = value
        }

    private var storedVolumeGestureEnabled by settingsKSafe(true, key = "bolo_live_volume_gesture_enabled")
    private var currentVolumeGestureEnabled by mutableStateOf(storedVolumeGestureEnabled)
    var volumeGestureEnabled: Boolean
        get() = currentVolumeGestureEnabled
        set(value) {
            if (value == currentVolumeGestureEnabled) return
            storedVolumeGestureEnabled = value
            currentVolumeGestureEnabled = value
        }

    private var storedPauseGestureEnabled by settingsKSafe(false, key = "bolo_live_pause_gesture_enabled")
    private var currentPauseGestureEnabled by mutableStateOf(storedPauseGestureEnabled)
    var pauseGestureEnabled: Boolean
        get() = currentPauseGestureEnabled
        set(value) {
            if (value == currentPauseGestureEnabled) return
            storedPauseGestureEnabled = value
            currentPauseGestureEnabled = value
        }

    private var storedDesktopDoubleClickPauseEnabled by settingsKSafe(false, key = "bolo_live_desktop_double_click_pause_enabled")
    private var currentDesktopDoubleClickPauseEnabled by mutableStateOf(storedDesktopDoubleClickPauseEnabled)
    var desktopDoubleClickPauseEnabled: Boolean
        get() = currentDesktopDoubleClickPauseEnabled
        set(value) {
            if (value == currentDesktopDoubleClickPauseEnabled) return
            storedDesktopDoubleClickPauseEnabled = value
            currentDesktopDoubleClickPauseEnabled = value
        }

    private var storedDesktopDefaultWindowFullscreenEnabled by settingsKSafe(
        false,
        key = "bolo_live_desktop_default_window_fullscreen_enabled",
    )
    private var currentDesktopDefaultWindowFullscreenEnabled by mutableStateOf(storedDesktopDefaultWindowFullscreenEnabled)
    var desktopDefaultWindowFullscreenEnabled: Boolean
        get() = currentDesktopDefaultWindowFullscreenEnabled
        set(value) {
            if (value == currentDesktopDefaultWindowFullscreenEnabled) return
            storedDesktopDefaultWindowFullscreenEnabled = value
            currentDesktopDefaultWindowFullscreenEnabled = value
        }

    private var storedDesktopVolumePercent by settingsKSafe(100, key = "bolo_live_desktop_volume_percent")
    private var currentDesktopVolumePercent by mutableIntStateOf(storedDesktopVolumePercent.coerceIn(0, 200))
    var desktopVolumePercent: Int
        get() = currentDesktopVolumePercent
        set(value) {
            val percent = value.coerceIn(0, 200)
            if (percent == currentDesktopVolumePercent) return
            storedDesktopVolumePercent = percent
            currentDesktopVolumePercent = percent
        }

    private var storedDesktopMuted by settingsKSafe(false, key = "bolo_live_desktop_muted")
    private var currentDesktopMuted by mutableStateOf(storedDesktopMuted)
    var desktopMuted: Boolean
        get() = currentDesktopMuted
        set(value) {
            if (value == currentDesktopMuted) return
            storedDesktopMuted = value
            currentDesktopMuted = value
        }

    private var storedDefaultVideoCodec by settingsKSafe(1, key = "bolo_live_default_video_codec")
    private var currentDefaultVideoCodec by mutableStateOf(
        LiveVideoCodec.fromCode(storedDefaultVideoCodec) ?: LiveVideoCodec.HEVC,
    )
    var defaultVideoCodec: LiveVideoCodec
        get() = currentDefaultVideoCodec
        set(value) {
            if (storedDefaultVideoCodec == value.code) return
            storedDefaultVideoCodec = value.code
            currentDefaultVideoCodec = value
        }

    private var storedDefaultVideoQuality by settingsKSafe(10000, key = "bolo_live_default_video_quality")
    private var currentDefaultVideoQuality by mutableStateOf(
        LiveVideoQuality.fromCode(storedDefaultVideoQuality) ?: LiveVideoQuality.ORIGINAL,
    )
    var defaultVideoQuality: LiveVideoQuality
        get() = currentDefaultVideoQuality
        set(value) {
            if (storedDefaultVideoQuality == value.code) return
            storedDefaultVideoQuality = value.code
            currentDefaultVideoQuality = value
        }

    private var storedRecordQualitySelectionEnabled by settingsKSafe(true, key = "bolo_live_record_quality_selection_enabled")
    private var currentRecordQualitySelectionEnabled by mutableStateOf(storedRecordQualitySelectionEnabled)
    var recordQualitySelectionEnabled: Boolean
        get() = currentRecordQualitySelectionEnabled
        set(value) {
            if (currentRecordQualitySelectionEnabled == value) return
            storedRecordQualitySelectionEnabled = value
            currentRecordQualitySelectionEnabled = value
        }

    private var storedOptimizePlaybackSourceEnabled by settingsKSafe(true, key = "bolo_live_optimize_playback_source_enabled")
    private var currentOptimizePlaybackSourceEnabled by mutableStateOf(storedOptimizePlaybackSourceEnabled)
    var optimizePlaybackSourceEnabled: Boolean
        get() = currentOptimizePlaybackSourceEnabled
        set(value) {
            if (currentOptimizePlaybackSourceEnabled == value) return
            storedOptimizePlaybackSourceEnabled = value
            currentOptimizePlaybackSourceEnabled = value
        }

    private var storedPlaybackBufferDisabled by settingsKSafe(false, key = "bolo_live_playback_buffer_disabled")
    private var currentPlaybackBufferDisabled by mutableStateOf(storedPlaybackBufferDisabled)
    var playbackBufferDisabled: Boolean
        get() = currentPlaybackBufferDisabled
        set(value) {
            if (currentPlaybackBufferDisabled == value) return
            storedPlaybackBufferDisabled = value
            currentPlaybackBufferDisabled = value
        }

    private var storedLocalReplayEnabled by settingsKSafe(false, key = "bolo_live_local_replay_enabled")
    private var currentLocalReplayEnabled by mutableStateOf(storedLocalReplayEnabled)
    var localReplayEnabled: Boolean
        get() = currentLocalReplayEnabled
        set(value) {
            if (currentLocalReplayEnabled == value) return
            storedLocalReplayEnabled = value
            currentLocalReplayEnabled = value
        }

    private var storedCacheSizeMiB by settingsKSafe(256, key = "bolo_live_cache_size_mib")
    private var currentCacheSizeMiB by mutableStateOf(normalizeCacheSizeMiB(storedCacheSizeMiB))
    var cacheSizeMiB: Int
        get() = currentCacheSizeMiB
        set(value) {
            val normalized = normalizeCacheSizeMiB(value)
            if (storedCacheSizeMiB == normalized) return
            storedCacheSizeMiB = normalized
            currentCacheSizeMiB = normalized
        }

    private fun normalizeCacheSizeMiB(value: Int): Int = when (value) {
        64, 128, 256, 512, 1024 -> value
        else -> 256
    }
}
