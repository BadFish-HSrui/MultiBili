package tv.hsrui.network.feature.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
internal data class RawHighEnergyProgressData(
    @SerialName("step_sec") private val stepSeconds: Double = 0.0,
    private val events: JsonObject = JsonObject(emptyMap()),
) {
    fun toData(): HighEnergyProgressData? {
        val values = (events["default"] as? JsonArray)?.map {
            (it as? JsonPrimitive)?.doubleOrNull ?: Double.NaN
        } ?: return null
        if (!stepSeconds.isFinite() || stepSeconds <= 0.0 ||
            values.count { it.isFinite() && it >= 0.0 } < 2
        ) return null
        // 无效点保留时间位置，不能移除后让后续采样向前错位。
        val samples = values.map { if (it.isFinite() && it >= 0.0) it else 0.0 }
        if (samples.none { it > 0.0 }) return null
        return HighEnergyProgressData(stepSeconds, samples)
    }
}

data class HighEnergyProgressData(
    val stepSeconds: Double,
    val samples: List<Double>,
)
