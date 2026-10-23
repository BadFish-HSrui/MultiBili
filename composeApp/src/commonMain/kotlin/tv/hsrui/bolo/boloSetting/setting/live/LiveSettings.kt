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
}
