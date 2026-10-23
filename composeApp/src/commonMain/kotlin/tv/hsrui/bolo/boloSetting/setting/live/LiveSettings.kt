package tv.hsrui.bolo.boloSetting.setting.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoCodec
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality

class LiveSettings(settingsKSafe: KSafePlain) {
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
