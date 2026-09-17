package tv.hsrui.bolo.boloSetting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import kotlin.math.roundToInt

class BoloSettings(settingsKSafe: KSafePlain) {
    private var storedPlayerAutoPlayOnOpenEnabled by settingsKSafe(
        true,
        key = "player_auto_play_on_open_enabled",
    )
    private var currentPlayerAutoPlayOnOpenEnabled by mutableStateOf(storedPlayerAutoPlayOnOpenEnabled)

    var playerAutoPlayOnOpenEnabled: Boolean
        get() = currentPlayerAutoPlayOnOpenEnabled
        set(value) {
            if (value == currentPlayerAutoPlayOnOpenEnabled) return
            storedPlayerAutoPlayOnOpenEnabled = value
            currentPlayerAutoPlayOnOpenEnabled = value
        }

    private var storedPlayerAutoEnableDanmakuOnOpenEnabled by settingsKSafe(
        true,
        key = "player_auto_enable_danmaku_on_open_enabled",
    )
    private var currentPlayerAutoEnableDanmakuOnOpenEnabled by mutableStateOf(storedPlayerAutoEnableDanmakuOnOpenEnabled)

    var playerAutoEnableDanmakuOnOpenEnabled: Boolean
        get() = currentPlayerAutoEnableDanmakuOnOpenEnabled
        set(value) {
            if (value == currentPlayerAutoEnableDanmakuOnOpenEnabled) return
            storedPlayerAutoEnableDanmakuOnOpenEnabled = value
            currentPlayerAutoEnableDanmakuOnOpenEnabled = value
        }

    private var storedPlayerDefaultVideoQuality by settingsKSafe(120, key = "player_default_video_quality")
    private var currentPlayerDefaultVideoQuality by mutableStateOf(
        VideoQuality.entries.firstOrNull { it.code == storedPlayerDefaultVideoQuality } ?: VideoQuality.UHD,
    )

    var playerDefaultVideoQuality: VideoQuality
        get() = currentPlayerDefaultVideoQuality
        set(value) {
            if (storedPlayerDefaultVideoQuality == value.code) return
            storedPlayerDefaultVideoQuality = value.code
            currentPlayerDefaultVideoQuality = value
        }

    private var storedPlayerDefaultAudioQuality by settingsKSafe(30280, key = "player_default_audio_quality")
    private var currentPlayerDefaultAudioQuality by mutableStateOf(
        normalizeDefaultAudioQuality(AudioQuality.entries.firstOrNull { it.code == storedPlayerDefaultAudioQuality }),
    )

    var playerDefaultAudioQuality: AudioQuality
        get() = currentPlayerDefaultAudioQuality
        set(value) {
            val normalized = normalizeDefaultAudioQuality(value)
            if (storedPlayerDefaultAudioQuality == normalized.code) return
            storedPlayerDefaultAudioQuality = normalized.code
            currentPlayerDefaultAudioQuality = normalized
        }

    private fun normalizeDefaultAudioQuality(value: AudioQuality?): AudioQuality = when (value) {
        AudioQuality.QUALITY_64K, AudioQuality.QUALITY_132K -> value
        else -> AudioQuality.QUALITY_192K
    }

    private var storedPlayerHideAudioQualitySelectorEnabled by settingsKSafe(
        true, key = "player_hide_audio_quality_selector_enabled",
    )
    private var currentPlayerHideAudioQualitySelectorEnabled by mutableStateOf(storedPlayerHideAudioQualitySelectorEnabled)

    var playerHideAudioQualitySelectorEnabled: Boolean
        get() = currentPlayerHideAudioQualitySelectorEnabled
        set(value) {
            if (value == currentPlayerHideAudioQualitySelectorEnabled) return
            storedPlayerHideAudioQualitySelectorEnabled = value
            currentPlayerHideAudioQualitySelectorEnabled = value
        }

    private var storedPlayerRecordQualitySelectionEnabled by settingsKSafe(
        true, key = "player_record_quality_selection_enabled",
    )
    private var currentPlayerRecordQualitySelectionEnabled by mutableStateOf(storedPlayerRecordQualitySelectionEnabled)

    var playerRecordQualitySelectionEnabled: Boolean
        get() = currentPlayerRecordQualitySelectionEnabled
        set(value) {
            if (value == currentPlayerRecordQualitySelectionEnabled) return
            storedPlayerRecordQualitySelectionEnabled = value
            currentPlayerRecordQualitySelectionEnabled = value
        }

    private var storedPlayerLoudnessMode by settingsKSafe("standard", key = "player_loudness_mode")
    private var currentPlayerLoudnessMode by mutableStateOf(
        PlaybackLoudnessMode.entries.firstOrNull { it.storedValue == storedPlayerLoudnessMode }
            ?: PlaybackLoudnessMode.Standard,
    )

    var playerLoudnessMode: PlaybackLoudnessMode
        get() = currentPlayerLoudnessMode
        set(value) {
            if (value == currentPlayerLoudnessMode) return
            storedPlayerLoudnessMode = value.storedValue
            currentPlayerLoudnessMode = value
        }

    private var storedPlayerDynamicLoudnessEnabled by settingsKSafe(true, key = "player_dynamic_loudness_enabled")
    private var currentPlayerDynamicLoudnessEnabled by mutableStateOf(storedPlayerDynamicLoudnessEnabled)

    var playerDynamicLoudnessEnabled: Boolean
        get() = currentPlayerDynamicLoudnessEnabled
        set(value) {
            if (value == currentPlayerDynamicLoudnessEnabled) return
            storedPlayerDynamicLoudnessEnabled = value
            currentPlayerDynamicLoudnessEnabled = value
        }

    private var storedPlayerDynamicLoudnessTargetLufs by settingsKSafe(-14f, key = "player_dynamic_loudness_target_lufs")
    private var currentPlayerDynamicLoudnessTargetLufs by mutableFloatStateOf(normalizeDynamicLoudnessTargetLufs(storedPlayerDynamicLoudnessTargetLufs))

    var playerDynamicLoudnessTargetLufs: Float
        get() = currentPlayerDynamicLoudnessTargetLufs
        set(value) {
            val normalized = normalizeDynamicLoudnessTargetLufs(value)
            if (normalized == currentPlayerDynamicLoudnessTargetLufs) return
            storedPlayerDynamicLoudnessTargetLufs = normalized
            currentPlayerDynamicLoudnessTargetLufs = normalized
        }

    private fun normalizeDynamicLoudnessTargetLufs(value: Float): Float =
        (value.takeIf { it.isFinite() } ?: -14f).coerceIn(-20f, -8f).roundToInt().toFloat()

    private var storedPlayerDynamicLoudnessRangeLu by settingsKSafe(11f, key = "player_dynamic_loudness_range_lu")
    private var currentPlayerDynamicLoudnessRangeLu by mutableFloatStateOf(normalizeDynamicLoudnessRangeLu(storedPlayerDynamicLoudnessRangeLu))

    var playerDynamicLoudnessRangeLu: Float
        get() = currentPlayerDynamicLoudnessRangeLu
        set(value) {
            val normalized = normalizeDynamicLoudnessRangeLu(value)
            if (normalized == currentPlayerDynamicLoudnessRangeLu) return
            storedPlayerDynamicLoudnessRangeLu = normalized
            currentPlayerDynamicLoudnessRangeLu = normalized
        }

    private fun normalizeDynamicLoudnessRangeLu(value: Float): Float =
        (value.takeIf { it.isFinite() } ?: 11f).coerceIn(6f, 16f).roundToInt().toFloat()

    private var storedPlayerDynamicLoudnessTruePeakDbtp by settingsKSafe(-2f, key = "player_dynamic_loudness_true_peak_dbtp")
    private var currentPlayerDynamicLoudnessTruePeakDbtp by mutableFloatStateOf(normalizeDynamicLoudnessTruePeakDbtp(storedPlayerDynamicLoudnessTruePeakDbtp))

    var playerDynamicLoudnessTruePeakDbtp: Float
        get() = currentPlayerDynamicLoudnessTruePeakDbtp
        set(value) {
            val normalized = normalizeDynamicLoudnessTruePeakDbtp(value)
            if (normalized == currentPlayerDynamicLoudnessTruePeakDbtp) return
            storedPlayerDynamicLoudnessTruePeakDbtp = normalized
            currentPlayerDynamicLoudnessTruePeakDbtp = normalized
        }

    private fun normalizeDynamicLoudnessTruePeakDbtp(value: Float): Float =
        ((value.takeIf { it.isFinite() } ?: -2f).coerceIn(-4f, 0f) * 2f).roundToInt() / 2f

    private var storedDanmakuEnabled by settingsKSafe(true, key = "danmaku_enabled")
    private var currentDanmakuEnabled by mutableStateOf(storedDanmakuEnabled)

    var danmakuEnabled: Boolean
        get() = currentDanmakuEnabled
        set(value) {
            if (value == currentDanmakuEnabled) return
            storedDanmakuEnabled = value
            currentDanmakuEnabled = value
        }

    private var storedPlayerReportStartEnabled by settingsKSafe(true, key = "player_report_start_enabled")
    private var currentPlayerReportStartEnabled by mutableStateOf(storedPlayerReportStartEnabled)

    var playerReportStartEnabled: Boolean
        get() = currentPlayerReportStartEnabled
        set(value) {
            if (value == currentPlayerReportStartEnabled) return
            storedPlayerReportStartEnabled = value
            currentPlayerReportStartEnabled = value
        }

    private var storedPlayerReportProgressMode by settingsKSafe("on_exit", key = "player_report_progress_mode")
    private var currentPlayerReportProgressMode by mutableStateOf(
        PlaybackProgressReportMode.entries.firstOrNull { it.storedValue == storedPlayerReportProgressMode }
            ?: PlaybackProgressReportMode.OnExit,
    )

    var playerReportProgressMode: PlaybackProgressReportMode
        get() = currentPlayerReportProgressMode
        set(value) {
            if (value == currentPlayerReportProgressMode) return
            storedPlayerReportProgressMode = value.storedValue
            currentPlayerReportProgressMode = value
        }

    private var storedPlayerReportProgressImmediatelyEnabled by settingsKSafe(
        false,
        key = "player_report_progress_immediately_enabled",
    )
    private var currentPlayerReportProgressImmediatelyEnabled by mutableStateOf(storedPlayerReportProgressImmediatelyEnabled)

    var playerReportProgressImmediatelyEnabled: Boolean
        get() = currentPlayerReportProgressImmediatelyEnabled
        set(value) {
            if (value == currentPlayerReportProgressImmediatelyEnabled) return
            storedPlayerReportProgressImmediatelyEnabled = value
            currentPlayerReportProgressImmediatelyEnabled = value
        }

    private var storedPlayerResumeAfterBackgroundEnabled by settingsKSafe(
        false,
        key = "player_resume_after_background_enabled",
    )
    private var currentPlayerResumeAfterBackgroundEnabled by mutableStateOf(storedPlayerResumeAfterBackgroundEnabled)

    var playerResumeAfterBackgroundEnabled: Boolean
        get() = currentPlayerResumeAfterBackgroundEnabled
        set(value) {
            if (value == currentPlayerResumeAfterBackgroundEnabled) return
            storedPlayerResumeAfterBackgroundEnabled = value
            currentPlayerResumeAfterBackgroundEnabled = value
        }

    private var storedPlayerAutoPlayAfterSeekEnabled by settingsKSafe(
        false,
        key = "player_auto_play_after_seek_enabled",
    )
    private var currentPlayerAutoPlayAfterSeekEnabled by mutableStateOf(storedPlayerAutoPlayAfterSeekEnabled)

    var playerAutoPlayAfterSeekEnabled: Boolean
        get() = currentPlayerAutoPlayAfterSeekEnabled
        set(value) {
            if (value == currentPlayerAutoPlayAfterSeekEnabled) return
            storedPlayerAutoPlayAfterSeekEnabled = value
            currentPlayerAutoPlayAfterSeekEnabled = value
        }

    private var storedPlayerAutoReplayEnabled by settingsKSafe(false, key = "player_auto_replay_enabled")
    private var currentPlayerAutoReplayEnabled by mutableStateOf(storedPlayerAutoReplayEnabled)

    var playerAutoReplayEnabled: Boolean
        get() = currentPlayerAutoReplayEnabled
        set(value) {
            if (value == currentPlayerAutoReplayEnabled) return
            storedPlayerAutoReplayEnabled = value
            currentPlayerAutoReplayEnabled = value
        }

    private var storedPlayerMergeAudioChannelsEnabled by settingsKSafe(
        false,
        key = "player_merge_audio_channels_enabled",
    )
    private var currentPlayerMergeAudioChannelsEnabled by mutableStateOf(storedPlayerMergeAudioChannelsEnabled)

    var playerMergeAudioChannelsEnabled: Boolean
        get() = currentPlayerMergeAudioChannelsEnabled
        set(value) {
            if (value == currentPlayerMergeAudioChannelsEnabled) return
            storedPlayerMergeAudioChannelsEnabled = value
            currentPlayerMergeAudioChannelsEnabled = value
        }

    private var storedPlayerDesktopVolumePercent by settingsKSafe(
        100,
        key = "player_desktop_volume_percent",
    )
    private var currentPlayerDesktopVolumePercent by mutableIntStateOf(
        storedPlayerDesktopVolumePercent.coerceIn(0, 200),
    )

    var playerDesktopVolumePercent: Int
        get() = currentPlayerDesktopVolumePercent
        set(value) {
            val percent = value.coerceIn(0, 200)
            if (percent == currentPlayerDesktopVolumePercent) return
            storedPlayerDesktopVolumePercent = percent
            currentPlayerDesktopVolumePercent = percent
        }

    private var storedPlayerDesktopMuted by settingsKSafe(false, key = "player_desktop_muted")
    private var currentPlayerDesktopMuted by mutableStateOf(storedPlayerDesktopMuted)

    var playerDesktopMuted: Boolean
        get() = currentPlayerDesktopMuted
        set(value) {
            if (value == currentPlayerDesktopMuted) return
            storedPlayerDesktopMuted = value
            currentPlayerDesktopMuted = value
        }

    private var storedPlayerDesktopDoubleClickPauseEnabled by settingsKSafe(
        false,
        key = "player_desktop_double_click_pause_enabled",
    )
    private var currentPlayerDesktopDoubleClickPauseEnabled by mutableStateOf(storedPlayerDesktopDoubleClickPauseEnabled)

    var playerDesktopDoubleClickPauseEnabled: Boolean
        get() = currentPlayerDesktopDoubleClickPauseEnabled
        set(value) {
            if (value == currentPlayerDesktopDoubleClickPauseEnabled) return
            storedPlayerDesktopDoubleClickPauseEnabled = value
            currentPlayerDesktopDoubleClickPauseEnabled = value
        }

    private var storedPlayerDesktopDefaultWindowFullscreenEnabled by settingsKSafe(
        false,
        key = "player_desktop_default_window_fullscreen_enabled",
    )
    private var currentPlayerDesktopDefaultWindowFullscreenEnabled by mutableStateOf(storedPlayerDesktopDefaultWindowFullscreenEnabled)

    var playerDesktopDefaultWindowFullscreenEnabled: Boolean
        get() = currentPlayerDesktopDefaultWindowFullscreenEnabled
        set(value) {
            if (value == currentPlayerDesktopDefaultWindowFullscreenEnabled) return
            storedPlayerDesktopDefaultWindowFullscreenEnabled = value
            currentPlayerDesktopDefaultWindowFullscreenEnabled = value
        }

    private var storedPlayerDesktopFastForwardHoldSpeedEnabled by settingsKSafe(
        false,
        key = "player_desktop_fast_forward_hold_speed_enabled",
    )
    private var currentPlayerDesktopFastForwardHoldSpeedEnabled by mutableStateOf(storedPlayerDesktopFastForwardHoldSpeedEnabled)

    var playerDesktopFastForwardHoldSpeedEnabled: Boolean
        get() = currentPlayerDesktopFastForwardHoldSpeedEnabled
        set(value) {
            if (value == currentPlayerDesktopFastForwardHoldSpeedEnabled) return
            storedPlayerDesktopFastForwardHoldSpeedEnabled = value
            currentPlayerDesktopFastForwardHoldSpeedEnabled = value
        }

    private var storedPlayerSeekGestureEnabled by settingsKSafe(
        true,
        key = "player_seek_gesture_enabled",
    )
    private var currentPlayerSeekGestureEnabled by mutableStateOf(storedPlayerSeekGestureEnabled)

    var playerSeekGestureEnabled: Boolean
        get() = currentPlayerSeekGestureEnabled
        set(value) {
            if (value == currentPlayerSeekGestureEnabled) return
            storedPlayerSeekGestureEnabled = value
            currentPlayerSeekGestureEnabled = value
        }

    private var storedPlayerBrightnessGestureEnabled by settingsKSafe(
        true,
        key = "player_brightness_gesture_enabled",
    )
    private var currentPlayerBrightnessGestureEnabled by mutableStateOf(storedPlayerBrightnessGestureEnabled)

    var playerBrightnessGestureEnabled: Boolean
        get() = currentPlayerBrightnessGestureEnabled
        set(value) {
            if (value == currentPlayerBrightnessGestureEnabled) return
            storedPlayerBrightnessGestureEnabled = value
            currentPlayerBrightnessGestureEnabled = value
        }

    private var storedPlayerVolumeGestureEnabled by settingsKSafe(
        true,
        key = "player_volume_gesture_enabled",
    )
    private var currentPlayerVolumeGestureEnabled by mutableStateOf(storedPlayerVolumeGestureEnabled)

    var playerVolumeGestureEnabled: Boolean
        get() = currentPlayerVolumeGestureEnabled
        set(value) {
            if (value == currentPlayerVolumeGestureEnabled) return
            storedPlayerVolumeGestureEnabled = value
            currentPlayerVolumeGestureEnabled = value
        }

    private var storedPlayerSideDoubleTapSeekEnabled by settingsKSafe(
        false,
        key = "player_side_double_tap_seek_enabled",
    )
    private var currentPlayerSideDoubleTapSeekEnabled by mutableStateOf(storedPlayerSideDoubleTapSeekEnabled)

    var playerSideDoubleTapSeekEnabled: Boolean
        get() = currentPlayerSideDoubleTapSeekEnabled
        set(value) {
            if (value == currentPlayerSideDoubleTapSeekEnabled) return
            storedPlayerSideDoubleTapSeekEnabled = value
            currentPlayerSideDoubleTapSeekEnabled = value
        }

    private var storedPlayerDoubleTapSeekSeconds by settingsKSafe(
        10,
        key = "player_double_tap_seek_seconds",
    )
    private var currentPlayerDoubleTapSeekSeconds by mutableIntStateOf(
        storedPlayerDoubleTapSeekSeconds.coerceIn(5, 30),
    )

    var playerDoubleTapSeekSeconds: Int
        get() = currentPlayerDoubleTapSeekSeconds
        set(value) {
            val seconds = value.coerceIn(5, 30)
            if (seconds == currentPlayerDoubleTapSeekSeconds) return
            storedPlayerDoubleTapSeekSeconds = seconds
            currentPlayerDoubleTapSeekSeconds = seconds
        }

    private var storedPlayerLongPressSpeedGestureEnabled by settingsKSafe(
        false,
        key = "player_long_press_speed_gesture_enabled",
    )
    private var currentPlayerLongPressSpeedGestureEnabled by mutableStateOf(
        storedPlayerLongPressSpeedGestureEnabled,
    )

    var playerLongPressSpeedGestureEnabled: Boolean
        get() = currentPlayerLongPressSpeedGestureEnabled
        set(value) {
            if (value == currentPlayerLongPressSpeedGestureEnabled) return
            storedPlayerLongPressSpeedGestureEnabled = value
            currentPlayerLongPressSpeedGestureEnabled = value
        }

    private var storedPlayerLongPressSpeedPercent by settingsKSafe(
        300,
        key = "player_long_press_speed_percent",
    )
    private var currentPlayerLongPressSpeedPercent by mutableIntStateOf(
        normalizePlayerLongPressSpeedPercent(storedPlayerLongPressSpeedPercent),
    )

    var playerLongPressSpeed: Float
        get() = currentPlayerLongPressSpeedPercent / 100f
        set(value) {
            val percent = normalizePlayerLongPressSpeedPercent((value * 100f).roundToInt())
            if (percent == currentPlayerLongPressSpeedPercent) return
            storedPlayerLongPressSpeedPercent = percent
            currentPlayerLongPressSpeedPercent = percent
        }

    private fun normalizePlayerLongPressSpeedPercent(value: Int): Int {
        if (value <= 0) return 300
        val stepsFromMinimum = ((value - 125) / 25f).roundToInt()
        return (125 + stepsFromMinimum * 25).coerceIn(125, 300)
    }

    private var storedDanmakuFilterLevel by settingsKSafe(0, key = "danmaku_filter_level")
    private var currentDanmakuFilterLevel by mutableIntStateOf(storedDanmakuFilterLevel.coerceIn(0, 10))

    var danmakuFilterLevel: Int
        get() = currentDanmakuFilterLevel
        set(value) {
            val level = value.coerceIn(0, 10)
            if (level == currentDanmakuFilterLevel) return
            storedDanmakuFilterLevel = level
            currentDanmakuFilterLevel = level
        }

    private var storedDanmakuScale by settingsKSafe(1.0f, key = "danmaku_scale_factor")
    private var currentDanmakuScale by mutableFloatStateOf(storedDanmakuScale)

    var danmakuScale: Float
        get() = currentDanmakuScale
        set(value) {
            val scale = if (value.isNaN()) 1.0f else value.coerceIn(0.5f, 2.0f)
            storedDanmakuScale = scale
            currentDanmakuScale = scale
        }

    private var storedDanmakuSpeed by settingsKSafe(1.0f, key = "danmaku_speed_factor")
    private var currentDanmakuSpeed by mutableFloatStateOf(normalizeDanmakuSpeed(storedDanmakuSpeed))

    var danmakuSpeed: Float
        get() = currentDanmakuSpeed
        set(value) {
            val speed = normalizeDanmakuSpeed(value)
            storedDanmakuSpeed = speed
            currentDanmakuSpeed = speed
        }

    private var storedDanmakuDisplayAreaRatio by settingsKSafe(1.0f, key = "danmaku_display_area_ratio")
    private var currentDanmakuDisplayAreaRatio by mutableFloatStateOf(
        normalizeDanmakuDisplayAreaRatio(storedDanmakuDisplayAreaRatio),
    )

    var danmakuDisplayAreaRatio: Float
        get() = currentDanmakuDisplayAreaRatio
        set(value) {
            val ratio = normalizeDanmakuDisplayAreaRatio(value)
            storedDanmakuDisplayAreaRatio = ratio
            currentDanmakuDisplayAreaRatio = ratio
        }

    private fun normalizeDanmakuDisplayAreaRatio(value: Float): Float =
        if (!value.isFinite()) 1.0f else (value.coerceIn(0.2f, 1.0f) * 20f).roundToInt() / 20f

    private var storedDanmakuTopBottomScrollEnabled by settingsKSafe(
        false,
        key = "danmaku_top_bottom_scroll_enabled",
    )
    private var currentDanmakuTopBottomScrollEnabled by mutableStateOf(storedDanmakuTopBottomScrollEnabled)

    var danmakuTopBottomScrollEnabled: Boolean
        get() = currentDanmakuTopBottomScrollEnabled
        set(value) {
            storedDanmakuTopBottomScrollEnabled = value
            currentDanmakuTopBottomScrollEnabled = value
        }

    private var storedDanmakuExtraLineSpacingEnabled by settingsKSafe(
        false,
        key = "danmaku_extra_line_spacing_enabled",
    )
    private var currentDanmakuExtraLineSpacingEnabled by mutableStateOf(storedDanmakuExtraLineSpacingEnabled)

    var danmakuExtraLineSpacingEnabled: Boolean
        get() = currentDanmakuExtraLineSpacingEnabled
        set(value) {
            storedDanmakuExtraLineSpacingEnabled = value
            currentDanmakuExtraLineSpacingEnabled = value
        }

    private var storedDanmakuScrollEnabled by settingsKSafe(true, key = "danmaku_scroll_enabled")
    private var currentDanmakuScrollEnabled by mutableStateOf(storedDanmakuScrollEnabled)

    var danmakuScrollEnabled: Boolean
        get() = currentDanmakuScrollEnabled
        set(value) {
            storedDanmakuScrollEnabled = value
            currentDanmakuScrollEnabled = value
        }

    private var storedDanmakuTopEnabled by settingsKSafe(true, key = "danmaku_top_enabled")
    private var currentDanmakuTopEnabled by mutableStateOf(storedDanmakuTopEnabled)

    var danmakuTopEnabled: Boolean
        get() = currentDanmakuTopEnabled
        set(value) {
            storedDanmakuTopEnabled = value
            currentDanmakuTopEnabled = value
        }

    private var storedDanmakuBottomEnabled by settingsKSafe(true, key = "danmaku_bottom_enabled")
    private var currentDanmakuBottomEnabled by mutableStateOf(storedDanmakuBottomEnabled)

    var danmakuBottomEnabled: Boolean
        get() = currentDanmakuBottomEnabled
        set(value) {
            storedDanmakuBottomEnabled = value
            currentDanmakuBottomEnabled = value
        }

    private var storedSubtitleAlwaysOn by settingsKSafe(false, key = "subtitle_always_on")
    private var currentSubtitleAlwaysOn by mutableStateOf(storedSubtitleAlwaysOn)

    var subtitleAlwaysOn: Boolean
        get() = currentSubtitleAlwaysOn
        set(value) {
            if (value == currentSubtitleAlwaysOn) return
            storedSubtitleAlwaysOn = value
            currentSubtitleAlwaysOn = value
        }

    private var storedSubtitleAutoChineseOnly by settingsKSafe(false, key = "subtitle_auto_chinese_only")
    private var currentSubtitleAutoChineseOnly by mutableStateOf(storedSubtitleAutoChineseOnly)

    var subtitleAutoChineseOnly: Boolean
        get() = currentSubtitleAutoChineseOnly
        set(value) {
            if (value == currentSubtitleAutoChineseOnly) return
            storedSubtitleAutoChineseOnly = value
            currentSubtitleAutoChineseOnly = value
        }

    private var storedSubtitleAutoExcludeAi by settingsKSafe(false, key = "subtitle_auto_exclude_ai")
    private var currentSubtitleAutoExcludeAi by mutableStateOf(storedSubtitleAutoExcludeAi)

    var subtitleAutoExcludeAi: Boolean
        get() = currentSubtitleAutoExcludeAi
        set(value) {
            if (value == currentSubtitleAutoExcludeAi) return
            storedSubtitleAutoExcludeAi = value
            currentSubtitleAutoExcludeAi = value
        }

    private var storedSubtitleScale by settingsKSafe(1.0f, key = "subtitle_scale_factor")
    private var currentSubtitleScale by mutableFloatStateOf(
        normalizeSubtitleValue(storedSubtitleScale, 1.0f, 0.5f, 2.0f),
    )
    private var previewSubtitleScale by mutableStateOf<Float?>(null)

    var subtitleScale: Float
        get() = currentSubtitleScale
        set(value) {
            val normalized = normalizeSubtitleValue(value, 1.0f, 0.5f, 2.0f)
            storedSubtitleScale = normalized
            currentSubtitleScale = normalized
        }

    val effectiveSubtitleScale: Float get() = previewSubtitleScale ?: currentSubtitleScale

    fun previewSubtitleScale(value: Float?) {
        previewSubtitleScale = value?.let { normalizeSubtitleValue(it, 1.0f, 0.5f, 2.0f) }
    }

    private var storedSubtitleHeightRatio by settingsKSafe(0.2f, key = "subtitle_height_ratio")
    private var currentSubtitleHeightRatio by mutableFloatStateOf(
        normalizeSubtitleValue(storedSubtitleHeightRatio, 0.2f, 0f, 1f),
    )
    private var previewSubtitleHeightRatio by mutableStateOf<Float?>(null)

    var subtitleHeightRatio: Float
        get() = currentSubtitleHeightRatio
        set(value) {
            val normalized = normalizeSubtitleValue(value, 0.2f, 0f, 1f)
            storedSubtitleHeightRatio = normalized
            currentSubtitleHeightRatio = normalized
        }

    val effectiveSubtitleHeightRatio: Float get() = previewSubtitleHeightRatio ?: currentSubtitleHeightRatio

    fun previewSubtitleHeightRatio(value: Float?) {
        previewSubtitleHeightRatio = value?.let { normalizeSubtitleValue(it, 0.2f, 0f, 1f) }
    }

    private var storedSubtitleBackgroundAlpha by settingsKSafe(0.7f, key = "subtitle_background_alpha")
    private var currentSubtitleBackgroundAlpha by mutableFloatStateOf(
        normalizeSubtitleValue(storedSubtitleBackgroundAlpha, 0.7f, 0f, 1f),
    )
    private var previewSubtitleBackgroundAlpha by mutableStateOf<Float?>(null)

    var subtitleBackgroundAlpha: Float
        get() = currentSubtitleBackgroundAlpha
        set(value) {
            val normalized = normalizeSubtitleValue(value, 0.7f, 0f, 1f)
            storedSubtitleBackgroundAlpha = normalized
            currentSubtitleBackgroundAlpha = normalized
        }

    val effectiveSubtitleBackgroundAlpha: Float get() = previewSubtitleBackgroundAlpha ?: currentSubtitleBackgroundAlpha

    fun previewSubtitleBackgroundAlpha(value: Float?) {
        previewSubtitleBackgroundAlpha = value?.let { normalizeSubtitleValue(it, 0.7f, 0f, 1f) }
    }

    private fun normalizeSubtitleValue(value: Float, default: Float, minimum: Float, maximum: Float): Float =
        if (!value.isFinite()) default else (value.coerceIn(minimum, maximum) * 100f).roundToInt() / 100f

    private fun normalizeDanmakuSpeed(value: Float): Float =
        if (value.isNaN()) 1.0f else value.coerceIn(0.5f, 2.0f)
}
