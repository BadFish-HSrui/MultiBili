package tv.hsrui.bolo.player.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import kotlin.math.roundToInt

class PlayerPlaybackSettings(settingsKSafe: KSafePlain) {
    private var storedResumeAfterBackgroundEnabled by settingsKSafe(
        false,
        key = "player_playback_resume_after_background_enabled",
    )
    private var currentResumeAfterBackgroundEnabled by mutableStateOf(storedResumeAfterBackgroundEnabled)

    var resumeAfterBackgroundEnabled: Boolean
        get() = currentResumeAfterBackgroundEnabled
        set(value) {
            if (value == currentResumeAfterBackgroundEnabled) return
            storedResumeAfterBackgroundEnabled = value
            currentResumeAfterBackgroundEnabled = value
        }

    private var storedAutoPlayAfterSeekEnabled by settingsKSafe(
        false,
        key = "player_playback_auto_play_after_seek_enabled",
    )
    private var currentAutoPlayAfterSeekEnabled by mutableStateOf(storedAutoPlayAfterSeekEnabled)

    var autoPlayAfterSeekEnabled: Boolean
        get() = currentAutoPlayAfterSeekEnabled
        set(value) {
            if (value == currentAutoPlayAfterSeekEnabled) return
            storedAutoPlayAfterSeekEnabled = value
            currentAutoPlayAfterSeekEnabled = value
        }

    private var storedHighEnergyProgressEnabled by settingsKSafe(
        true,
        key = "player_playback_high_energy_progress_enabled",
    )
    private var currentHighEnergyProgressEnabled by mutableStateOf(storedHighEnergyProgressEnabled)

    var highEnergyProgressEnabled: Boolean
        get() = currentHighEnergyProgressEnabled
        set(value) {
            if (value == currentHighEnergyProgressEnabled) return
            storedHighEnergyProgressEnabled = value
            currentHighEnergyProgressEnabled = value
        }

    private var storedHighEnergyProgressAlwaysVisible by settingsKSafe(
        false,
        key = "player_playback_high_energy_progress_always_visible",
    )
    private var currentHighEnergyProgressAlwaysVisible by mutableStateOf(storedHighEnergyProgressAlwaysVisible)

    var highEnergyProgressAlwaysVisible: Boolean
        get() = currentHighEnergyProgressAlwaysVisible
        set(value) {
            if (value == currentHighEnergyProgressAlwaysVisible) return
            storedHighEnergyProgressAlwaysVisible = value
            currentHighEnergyProgressAlwaysVisible = value
        }

    private var storedMergeAudioChannelsEnabled by settingsKSafe(
        false,
        key = "player_playback_merge_audio_channels_enabled",
    )
    private var currentMergeAudioChannelsEnabled by mutableStateOf(storedMergeAudioChannelsEnabled)

    var mergeAudioChannelsEnabled: Boolean
        get() = currentMergeAudioChannelsEnabled
        set(value) {
            if (value == currentMergeAudioChannelsEnabled) return
            storedMergeAudioChannelsEnabled = value
            currentMergeAudioChannelsEnabled = value
        }

    private var storedDesktopDoubleClickPauseEnabled by settingsKSafe(
        false,
        key = "player_playback_desktop_double_click_pause_enabled",
    )
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
        key = "player_playback_desktop_default_window_fullscreen_enabled",
    )
    private var currentDesktopDefaultWindowFullscreenEnabled by mutableStateOf(storedDesktopDefaultWindowFullscreenEnabled)

    var desktopDefaultWindowFullscreenEnabled: Boolean
        get() = currentDesktopDefaultWindowFullscreenEnabled
        set(value) {
            if (value == currentDesktopDefaultWindowFullscreenEnabled) return
            storedDesktopDefaultWindowFullscreenEnabled = value
            currentDesktopDefaultWindowFullscreenEnabled = value
        }

    private var storedDesktopFastForwardHoldSpeedEnabled by settingsKSafe(
        false,
        key = "player_playback_desktop_fast_forward_hold_speed_enabled",
    )
    private var currentDesktopFastForwardHoldSpeedEnabled by mutableStateOf(storedDesktopFastForwardHoldSpeedEnabled)

    var desktopFastForwardHoldSpeedEnabled: Boolean
        get() = currentDesktopFastForwardHoldSpeedEnabled
        set(value) {
            if (value == currentDesktopFastForwardHoldSpeedEnabled) return
            storedDesktopFastForwardHoldSpeedEnabled = value
            currentDesktopFastForwardHoldSpeedEnabled = value
        }

    private var storedSeekGestureEnabled by settingsKSafe(
        true,
        key = "player_playback_seek_gesture_enabled",
    )
    private var currentSeekGestureEnabled by mutableStateOf(storedSeekGestureEnabled)

    var seekGestureEnabled: Boolean
        get() = currentSeekGestureEnabled
        set(value) {
            if (value == currentSeekGestureEnabled) return
            storedSeekGestureEnabled = value
            currentSeekGestureEnabled = value
        }

    private var storedBrightnessGestureEnabled by settingsKSafe(
        true,
        key = "player_playback_brightness_gesture_enabled",
    )
    private var currentBrightnessGestureEnabled by mutableStateOf(storedBrightnessGestureEnabled)

    var brightnessGestureEnabled: Boolean
        get() = currentBrightnessGestureEnabled
        set(value) {
            if (value == currentBrightnessGestureEnabled) return
            storedBrightnessGestureEnabled = value
            currentBrightnessGestureEnabled = value
        }

    private var storedVolumeGestureEnabled by settingsKSafe(
        true,
        key = "player_playback_volume_gesture_enabled",
    )
    private var currentVolumeGestureEnabled by mutableStateOf(storedVolumeGestureEnabled)

    var volumeGestureEnabled: Boolean
        get() = currentVolumeGestureEnabled
        set(value) {
            if (value == currentVolumeGestureEnabled) return
            storedVolumeGestureEnabled = value
            currentVolumeGestureEnabled = value
        }

    private var storedSideDoubleTapSeekEnabled by settingsKSafe(
        false,
        key = "player_playback_side_double_tap_seek_enabled",
    )
    private var currentSideDoubleTapSeekEnabled by mutableStateOf(storedSideDoubleTapSeekEnabled)

    var sideDoubleTapSeekEnabled: Boolean
        get() = currentSideDoubleTapSeekEnabled
        set(value) {
            if (value == currentSideDoubleTapSeekEnabled) return
            storedSideDoubleTapSeekEnabled = value
            currentSideDoubleTapSeekEnabled = value
        }

    private var storedSeekForwardSeconds by settingsKSafe(
        10,
        key = "player_playback_seek_forward_seconds",
    )
    private var currentSeekForwardSeconds by mutableIntStateOf(
        storedSeekForwardSeconds.coerceIn(5, 30),
    )

    var seekForwardSeconds: Int
        get() = currentSeekForwardSeconds
        set(value) {
            val seconds = value.coerceIn(5, 30)
            if (seconds == currentSeekForwardSeconds) return
            storedSeekForwardSeconds = seconds
            currentSeekForwardSeconds = seconds
        }

    private var storedSeekBackwardSeconds by settingsKSafe(
        5,
        key = "player_playback_seek_backward_seconds",
    )
    private var currentSeekBackwardSeconds by mutableIntStateOf(
        storedSeekBackwardSeconds.coerceIn(5, 30),
    )

    var seekBackwardSeconds: Int
        get() = currentSeekBackwardSeconds
        set(value) {
            val seconds = value.coerceIn(5, 30)
            if (seconds == currentSeekBackwardSeconds) return
            storedSeekBackwardSeconds = seconds
            currentSeekBackwardSeconds = seconds
        }

    private var storedLongPressSpeedGestureEnabled by settingsKSafe(
        false,
        key = "player_playback_long_press_speed_gesture_enabled",
    )
    private var currentLongPressSpeedGestureEnabled by mutableStateOf(
        storedLongPressSpeedGestureEnabled,
    )

    var longPressSpeedGestureEnabled: Boolean
        get() = currentLongPressSpeedGestureEnabled
        set(value) {
            if (value == currentLongPressSpeedGestureEnabled) return
            storedLongPressSpeedGestureEnabled = value
            currentLongPressSpeedGestureEnabled = value
        }

    private var storedLongPressSpeedPercent by settingsKSafe(
        200,
        key = "player_playback_long_press_speed_percent",
    )
    private var currentLongPressSpeedPercent by mutableIntStateOf(
        normalizeLongPressSpeedPercent(storedLongPressSpeedPercent),
    )

    var longPressSpeed: Float
        get() = currentLongPressSpeedPercent / 100f
        set(value) {
            val percent = normalizeLongPressSpeedPercent((value * 100f).roundToInt())
            if (percent == currentLongPressSpeedPercent) return
            storedLongPressSpeedPercent = percent
            currentLongPressSpeedPercent = percent
        }

    private fun normalizeLongPressSpeedPercent(value: Int): Int {
        if (value <= 0) return 200
        val stepsFromMinimum = ((value - 125) / 25f).roundToInt()
        return (125 + stepsFromMinimum * 25).coerceIn(125, 300)
    }
}
