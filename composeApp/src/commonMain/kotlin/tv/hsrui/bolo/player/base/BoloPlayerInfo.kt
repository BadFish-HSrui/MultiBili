package tv.hsrui.bolo.player.base

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** 独立于播放控制状态；null 表示未知，码率为 bit/s，下载速度为 B/s。 */
data class BoloPlayerInfo(
    val video: BoloPlayerVideoInfo = BoloPlayerVideoInfo(),
    val audio: BoloPlayerAudioInfo? = null,
    val hardwareDecoder: String? = null,
    val hardwareInterop: String? = null,
    val videoOutput: String? = null,
    val audioOutput: String? = null,
    val downloadBytesPerSecond: Long? = null,
)

data class BoloPlayerVideoInfo(
    val codec: String? = null,
    val nominalBitrateBps: Long? = null,
    val playbackBitrateBps: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val decoder: String? = null,
    val decoderDescription: String? = null,
    val fragmentIndex: Int? = null,
    val fragmentCount: Int? = null,
    val downloadBytesPerSecond: Long? = null,
) {
    val aspectRatio: Float?
        get() = if (width != null && width > 0 && height != null && height > 0) {
            width.toFloat() / height
        } else null
}

data class BoloPlayerAudioInfo(
    val codec: String? = null,
    val nominalBitrateBps: Long? = null,
    val playbackBitrateBps: Long? = null,
    val sampleRateHz: Int? = null,
    val channelLayout: String? = null,
    val outputChannelLayout: String? = null,
    val outputChannelCount: Int? = null,
    val channelsMerged: Boolean? = null,
    val sampleFormat: String? = null,
    val decoder: String? = null,
    val decoderDescription: String? = null,
    val fragmentIndex: Int? = null,
    val fragmentCount: Int? = null,
    val downloadBytesPerSecond: Long? = null,
)

internal data class BoloMpvInfoSnapshot(
    val instance: BoloMpvBackend,
    val generation: Long,
    val info: BoloPlayerInfo,
) {
    companion object {
        fun parse(text: String, instance: BoloMpvBackend): BoloMpvInfoSnapshot? = runCatching {
            val envelope = Json.parseToJsonElement(text) as? JsonObject ?: return null
            val generation = envelope.number("generation") ?: return null
            val expectedEntry = envelope.number("expectedEntry") ?: return null
            val source = envelope["info"] as? JsonObject ?: return null
            if (source.number("entryId") != expectedEntry) return null
            val video = source["video"] as? JsonObject
            val audio = source["audio"] as? JsonObject
            BoloMpvInfoSnapshot(
                instance = instance,
                generation = generation,
                info = BoloPlayerInfo(
                    video = BoloPlayerVideoInfo(
                        codec = video?.string("codec"),
                        playbackBitrateBps = video?.number("playbackBitrateBps"),
                        width = video?.positiveInt("width"),
                        height = video?.positiveInt("height"),
                        fps = video?.decimal("fps"),
                        decoder = video?.string("decoder"),
                        decoderDescription = video?.string("decoderDescription"),
                        fragmentIndex = video?.positiveInt("fragmentIndex"),
                        fragmentCount = video?.positiveInt("fragmentCount"),
                        downloadBytesPerSecond = video?.number("downloadBytesPerSecond"),
                    ),
                    audio = audio?.let {
                        BoloPlayerAudioInfo(
                            codec = it.string("codec"),
                            playbackBitrateBps = it.number("playbackBitrateBps"),
                            sampleRateHz = it.positiveInt("sampleRateHz"),
                            channelLayout = it.string("channelLayout"),
                            outputChannelLayout = it.string("outputChannelLayout"),
                            outputChannelCount = it.positiveInt("outputChannelCount"),
                            channelsMerged = (it["channelsMerged"] as? JsonPrimitive)?.booleanOrNull,
                            sampleFormat = it.string("sampleFormat"),
                            decoder = it.string("decoder"),
                            decoderDescription = it.string("decoderDescription"),
                            fragmentIndex = it.positiveInt("fragmentIndex"),
                            fragmentCount = it.positiveInt("fragmentCount"),
                            downloadBytesPerSecond = it.number("downloadBytesPerSecond"),
                        )
                    },
                    hardwareDecoder = source.string("hardwareDecoder"),
                    hardwareInterop = source.string("hardwareInterop"),
                    videoOutput = source.string("videoOutput"),
                    audioOutput = source.string("audioOutput"),
                    downloadBytesPerSecond = source.number("downloadBytesPerSecond"),
                ),
            )
        }.getOrNull()
    }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.number(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }

private fun JsonObject.positiveInt(key: String): Int? =
    number(key)?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()

private fun JsonObject.decimal(key: String): Double? =
    (get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it > 0 }

internal fun BoloPlayerInfo.withoutDynamicValues(): BoloPlayerInfo = copy(
    video = video.copy(playbackBitrateBps = null, fragmentIndex = null, downloadBytesPerSecond = null),
    audio = audio?.copy(playbackBitrateBps = null, fragmentIndex = null, downloadBytesPerSecond = null),
    downloadBytesPerSecond = null,
)

internal fun parsePlayerFrameRate(value: String): Double? {
    val parts = value.split('/')
    val numerator = parts.firstOrNull()?.toDoubleOrNull() ?: return null
    val denominator = if (parts.size == 1) 1.0 else parts.getOrNull(1)?.toDoubleOrNull() ?: return null
    if (parts.size > 2 || denominator <= 0) return null
    return (numerator / denominator).takeIf { it.isFinite() && it > 0 }
}
