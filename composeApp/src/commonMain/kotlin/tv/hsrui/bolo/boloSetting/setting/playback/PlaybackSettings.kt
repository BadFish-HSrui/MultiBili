package tv.hsrui.bolo.boloSetting.setting.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import kotlin.math.roundToInt
import tv.hsrui.bolo.boloSetting.PlaybackEndBehavior
import tv.hsrui.bolo.boloSetting.PlaybackLoudnessMode
import tv.hsrui.bolo.boloSetting.PlaybackProgressReportMode
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality

class PlaybackSettings(settingsKSafe: KSafePlain) {
    private var storedAutoPlayOnOpenEnabled by settingsKSafe(
        true,
        key = "bolo_playback_auto_play_on_open_enabled",
    )
    private var currentAutoPlayOnOpenEnabled by mutableStateOf(storedAutoPlayOnOpenEnabled)

    var autoPlayOnOpenEnabled: Boolean
        get() = currentAutoPlayOnOpenEnabled
        set(value) {
            if (value == currentAutoPlayOnOpenEnabled) return
            storedAutoPlayOnOpenEnabled = value
            currentAutoPlayOnOpenEnabled = value
        }

    private var storedAutoEnableDanmakuOnOpenEnabled by settingsKSafe(
        false,
        key = "bolo_playback_auto_enable_danmaku_on_open_enabled",
    )
    private var currentAutoEnableDanmakuOnOpenEnabled by mutableStateOf(storedAutoEnableDanmakuOnOpenEnabled)

    var autoEnableDanmakuOnOpenEnabled: Boolean
        get() = currentAutoEnableDanmakuOnOpenEnabled
        set(value) {
            if (value == currentAutoEnableDanmakuOnOpenEnabled) return
            storedAutoEnableDanmakuOnOpenEnabled = value
            currentAutoEnableDanmakuOnOpenEnabled = value
        }

    private var storedResumeFromHistoryEnabled by settingsKSafe(
        true,
        key = "bolo_playback_resume_from_history_enabled",
    )
    private var currentResumeFromHistoryEnabled by mutableStateOf(storedResumeFromHistoryEnabled)

    var resumeFromHistoryEnabled: Boolean
        get() = currentResumeFromHistoryEnabled
        set(value) {
            if (value == currentResumeFromHistoryEnabled) return
            storedResumeFromHistoryEnabled = value
            currentResumeFromHistoryEnabled = value
        }

    private var storedDefaultVideoCodec by settingsKSafe(12, key = "bolo_playback_default_video_codec")
    private var currentDefaultVideoCodec by mutableStateOf(
        normalizeDefaultVideoCodec(VideoCodec.entries.firstOrNull { it.code == storedDefaultVideoCodec }),
    )

    var defaultVideoCodec: VideoCodec
        get() = currentDefaultVideoCodec
        set(value) {
            val normalized = normalizeDefaultVideoCodec(value)
            if (storedDefaultVideoCodec == normalized.code) return
            storedDefaultVideoCodec = normalized.code
            currentDefaultVideoCodec = normalized
        }

    private fun normalizeDefaultVideoCodec(value: VideoCodec?): VideoCodec = when (value) {
        VideoCodec.AVC, VideoCodec.AV1 -> value
        else -> VideoCodec.HEVC
    }

    private var storedDefaultVideoQuality by settingsKSafe(120, key = "bolo_playback_default_video_quality")
    private var currentDefaultVideoQuality by mutableStateOf(
        VideoQuality.entries.firstOrNull { it.code == storedDefaultVideoQuality } ?: VideoQuality.UHD,
    )

    var defaultVideoQuality: VideoQuality
        get() = currentDefaultVideoQuality
        set(value) {
            if (storedDefaultVideoQuality == value.code) return
            storedDefaultVideoQuality = value.code
            currentDefaultVideoQuality = value
        }

    private var storedDefaultAudioQuality by settingsKSafe(30280, key = "bolo_playback_default_audio_quality")
    private var currentDefaultAudioQuality by mutableStateOf(
        normalizeDefaultAudioQuality(AudioQuality.entries.firstOrNull { it.code == storedDefaultAudioQuality }),
    )

    var defaultAudioQuality: AudioQuality
        get() = currentDefaultAudioQuality
        set(value) {
            val normalized = normalizeDefaultAudioQuality(value)
            if (storedDefaultAudioQuality == normalized.code) return
            storedDefaultAudioQuality = normalized.code
            currentDefaultAudioQuality = normalized
        }

    private fun normalizeDefaultAudioQuality(value: AudioQuality?): AudioQuality = when (value) {
        AudioQuality.QUALITY_64K, AudioQuality.QUALITY_132K -> value
        else -> AudioQuality.QUALITY_192K
    }

    private var storedHideAudioQualitySelectorEnabled by settingsKSafe(
        true, key = "bolo_playback_hide_audio_quality_selector_enabled",
    )
    private var currentHideAudioQualitySelectorEnabled by mutableStateOf(storedHideAudioQualitySelectorEnabled)

    var hideAudioQualitySelectorEnabled: Boolean
        get() = currentHideAudioQualitySelectorEnabled
        set(value) {
            if (value == currentHideAudioQualitySelectorEnabled) return
            storedHideAudioQualitySelectorEnabled = value
            currentHideAudioQualitySelectorEnabled = value
        }

    private var storedRecordQualitySelectionEnabled by settingsKSafe(
        true, key = "bolo_playback_record_quality_selection_enabled",
    )
    private var currentRecordQualitySelectionEnabled by mutableStateOf(storedRecordQualitySelectionEnabled)

    var recordQualitySelectionEnabled: Boolean
        get() = currentRecordQualitySelectionEnabled
        set(value) {
            if (value == currentRecordQualitySelectionEnabled) return
            storedRecordQualitySelectionEnabled = value
            currentRecordQualitySelectionEnabled = value
        }

    private var storedOptimizePlaybackSourceEnabled by settingsKSafe(
        true, key = "bolo_playback_optimize_playback_source_enabled",
    )
    private var currentOptimizePlaybackSourceEnabled by mutableStateOf(storedOptimizePlaybackSourceEnabled)

    var optimizePlaybackSourceEnabled: Boolean
        get() = currentOptimizePlaybackSourceEnabled
        set(value) {
            if (value == currentOptimizePlaybackSourceEnabled) return
            storedOptimizePlaybackSourceEnabled = value
            currentOptimizePlaybackSourceEnabled = value
        }

    private var storedLoudnessMode by settingsKSafe("standard", key = "bolo_playback_loudness_mode")
    private var currentLoudnessMode by mutableStateOf(
        PlaybackLoudnessMode.entries.firstOrNull { it.storedValue == storedLoudnessMode }
            ?: PlaybackLoudnessMode.Standard,
    )

    var loudnessMode: PlaybackLoudnessMode
        get() = currentLoudnessMode
        set(value) {
            if (value == currentLoudnessMode) return
            storedLoudnessMode = value.storedValue
            currentLoudnessMode = value
        }

    private var storedDynamicLoudnessEnabled by settingsKSafe(true, key = "bolo_playback_dynamic_loudness_enabled")
    private var currentDynamicLoudnessEnabled by mutableStateOf(storedDynamicLoudnessEnabled)

    var dynamicLoudnessEnabled: Boolean
        get() = currentDynamicLoudnessEnabled
        set(value) {
            if (value == currentDynamicLoudnessEnabled) return
            storedDynamicLoudnessEnabled = value
            currentDynamicLoudnessEnabled = value
        }

    private var storedDynamicLoudnessTargetLufs by settingsKSafe(-14f, key = "bolo_playback_dynamic_loudness_target_lufs")
    private var currentDynamicLoudnessTargetLufs by mutableFloatStateOf(normalizeDynamicLoudnessTargetLufs(storedDynamicLoudnessTargetLufs))

    var dynamicLoudnessTargetLufs: Float
        get() = currentDynamicLoudnessTargetLufs
        set(value) {
            val normalized = normalizeDynamicLoudnessTargetLufs(value)
            if (normalized == currentDynamicLoudnessTargetLufs) return
            storedDynamicLoudnessTargetLufs = normalized
            currentDynamicLoudnessTargetLufs = normalized
        }

    private fun normalizeDynamicLoudnessTargetLufs(value: Float): Float =
        (value.takeIf { it.isFinite() } ?: -14f).coerceIn(-20f, -8f).roundToInt().toFloat()

    private var storedDynamicLoudnessRangeLu by settingsKSafe(11f, key = "bolo_playback_dynamic_loudness_range_lu")
    private var currentDynamicLoudnessRangeLu by mutableFloatStateOf(normalizeDynamicLoudnessRangeLu(storedDynamicLoudnessRangeLu))

    var dynamicLoudnessRangeLu: Float
        get() = currentDynamicLoudnessRangeLu
        set(value) {
            val normalized = normalizeDynamicLoudnessRangeLu(value)
            if (normalized == currentDynamicLoudnessRangeLu) return
            storedDynamicLoudnessRangeLu = normalized
            currentDynamicLoudnessRangeLu = normalized
        }

    private fun normalizeDynamicLoudnessRangeLu(value: Float): Float =
        (value.takeIf { it.isFinite() } ?: 11f).coerceIn(6f, 16f).roundToInt().toFloat()

    private var storedDynamicLoudnessTruePeakDbtp by settingsKSafe(-2f, key = "bolo_playback_dynamic_loudness_true_peak_dbtp")
    private var currentDynamicLoudnessTruePeakDbtp by mutableFloatStateOf(normalizeDynamicLoudnessTruePeakDbtp(storedDynamicLoudnessTruePeakDbtp))

    var dynamicLoudnessTruePeakDbtp: Float
        get() = currentDynamicLoudnessTruePeakDbtp
        set(value) {
            val normalized = normalizeDynamicLoudnessTruePeakDbtp(value)
            if (normalized == currentDynamicLoudnessTruePeakDbtp) return
            storedDynamicLoudnessTruePeakDbtp = normalized
            currentDynamicLoudnessTruePeakDbtp = normalized
        }

    private fun normalizeDynamicLoudnessTruePeakDbtp(value: Float): Float =
        ((value.takeIf { it.isFinite() } ?: -2f).coerceIn(-4f, 0f) * 2f).roundToInt() / 2f

    private var storedReportStartEnabled by settingsKSafe(true, key = "bolo_playback_report_start_enabled")
    private var currentReportStartEnabled by mutableStateOf(storedReportStartEnabled)

    var reportStartEnabled: Boolean
        get() = currentReportStartEnabled
        set(value) {
            if (value == currentReportStartEnabled) return
            storedReportStartEnabled = value
            currentReportStartEnabled = value
        }

    private var storedReportProgressMode by settingsKSafe("on_exit", key = "bolo_playback_report_progress_mode")
    private var currentReportProgressMode by mutableStateOf(
        PlaybackProgressReportMode.entries.firstOrNull { it.storedValue == storedReportProgressMode }
            ?: PlaybackProgressReportMode.OnExit,
    )

    var reportProgressMode: PlaybackProgressReportMode
        get() = currentReportProgressMode
        set(value) {
            if (value == currentReportProgressMode) return
            storedReportProgressMode = value.storedValue
            currentReportProgressMode = value
        }

    private var storedReportProgressImmediatelyEnabled by settingsKSafe(
        false,
        key = "bolo_playback_report_progress_immediately_enabled",
    )
    private var currentReportProgressImmediatelyEnabled by mutableStateOf(storedReportProgressImmediatelyEnabled)

    var reportProgressImmediatelyEnabled: Boolean
        get() = currentReportProgressImmediatelyEnabled
        set(value) {
            if (value == currentReportProgressImmediatelyEnabled) return
            storedReportProgressImmediatelyEnabled = value
            currentReportProgressImmediatelyEnabled = value
        }

    private var storedBackgroundPlaybackEnabled by settingsKSafe(
        false,
        key = "bolo_playback_background_playback_enabled",
    )
    private var currentBackgroundPlaybackEnabled by mutableStateOf(storedBackgroundPlaybackEnabled)

    var backgroundPlaybackEnabled: Boolean
        get() = currentBackgroundPlaybackEnabled
        set(value) {
            if (value == currentBackgroundPlaybackEnabled) return
            storedBackgroundPlaybackEnabled = value
            currentBackgroundPlaybackEnabled = value
        }

    private var storedEndBehavior by settingsKSafe("off", key = "bolo_playback_end_behavior")
    private var currentEndBehavior by mutableStateOf(
        PlaybackEndBehavior.entries.firstOrNull { it.storedValue == storedEndBehavior }
            ?: PlaybackEndBehavior.Off,
    )

    var endBehavior: PlaybackEndBehavior
        get() = currentEndBehavior
        set(value) {
            if (value == currentEndBehavior) return
            storedEndBehavior = value.storedValue
            currentEndBehavior = value
        }

    private var storedSubtitleAlwaysOn by settingsKSafe(false, key = "bolo_playback_subtitle_always_on")
    private var currentSubtitleAlwaysOn by mutableStateOf(storedSubtitleAlwaysOn)

    private var storedSubtitleSmartEnabled by settingsKSafe(true, key = "bolo_playback_subtitle_smart_enabled")
    private var currentSubtitleSmartEnabled by mutableStateOf(storedSubtitleSmartEnabled && !storedSubtitleAlwaysOn)

    init {
        if (storedSubtitleAlwaysOn && storedSubtitleSmartEnabled) storedSubtitleSmartEnabled = false
    }

    var subtitleSmartEnabled: Boolean
        get() = currentSubtitleSmartEnabled
        set(value) {
            if (value) {
                storedSubtitleAlwaysOn = false
                currentSubtitleAlwaysOn = false
            }
            storedSubtitleSmartEnabled = value
            currentSubtitleSmartEnabled = value
        }

    var subtitleAlwaysOn: Boolean
        get() = currentSubtitleAlwaysOn
        set(value) {
            if (value) {
                storedSubtitleSmartEnabled = false
                currentSubtitleSmartEnabled = false
            }
            if (value == currentSubtitleAlwaysOn) return
            storedSubtitleAlwaysOn = value
            currentSubtitleAlwaysOn = value
        }

    private var storedSubtitleAutoChineseOnly by settingsKSafe(false, key = "bolo_playback_subtitle_auto_chinese_only")
    private var currentSubtitleAutoChineseOnly by mutableStateOf(storedSubtitleAutoChineseOnly)

    var subtitleAutoChineseOnly: Boolean
        get() = currentSubtitleAutoChineseOnly
        set(value) {
            if (value == currentSubtitleAutoChineseOnly) return
            storedSubtitleAutoChineseOnly = value
            currentSubtitleAutoChineseOnly = value
        }

    private var storedSubtitleAutoExcludeAi by settingsKSafe(false, key = "bolo_playback_subtitle_auto_exclude_ai")
    private var currentSubtitleAutoExcludeAi by mutableStateOf(storedSubtitleAutoExcludeAi)

    var subtitleAutoExcludeAi: Boolean
        get() = currentSubtitleAutoExcludeAi
        set(value) {
            if (value == currentSubtitleAutoExcludeAi) return
            storedSubtitleAutoExcludeAi = value
            currentSubtitleAutoExcludeAi = value
        }
}
