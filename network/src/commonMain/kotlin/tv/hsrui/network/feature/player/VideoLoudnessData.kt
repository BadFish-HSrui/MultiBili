package tv.hsrui.network.feature.player

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

data class VideoLoudnessData(
    val measuredLoudnessLufs: Double,
    val standardTargetLoudnessLufs: Double,
    val highDynamicTargetLoudnessLufs: Double,
    val lowLoudnessThresholdLufs: Double,
) {
    companion object {
        internal fun fromJson(value: JsonElement?): VideoLoudnessData? {
            val volume = value as? JsonObject ?: return null
            val targets = volume["multi_scene_args"] as? JsonObject ?: return null
            fun JsonObject.number(key: String): Double? =
                (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

            return VideoLoudnessData(
                measuredLoudnessLufs = volume.number("measured_i") ?: return null,
                standardTargetLoudnessLufs = targets.number("normal_target_i") ?: return null,
                highDynamicTargetLoudnessLufs = targets.number("high_dynamic_target_i") ?: return null,
                lowLoudnessThresholdLufs = targets.number("undersized_target_i") ?: return null,
            )
        }
    }
}
