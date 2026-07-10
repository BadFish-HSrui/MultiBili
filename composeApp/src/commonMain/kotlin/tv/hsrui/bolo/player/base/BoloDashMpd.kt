package tv.hsrui.bolo.player.base

import tv.hsrui.network.feature.player.BiliDashObject

internal data class BoloDashMpd(
    val xml: String,
    val hasAudio: Boolean,
    val durationSec: Long,
    val videoSummary: String,
    val audioSummary: String?
)

fun BoloPlayerController.load(
    video: BiliDashObject,
    audio: BiliDashObject? = null,
    startPosition: Int = 0
) {
    val mpd = try {
        buildBoloDashMpd(video, audio)
    } catch (e: IllegalArgumentException) {
        reportLoadError(BoloPlayerError.FormatNotSupported(e.message ?: "DASH MPD 构建参数无效"))
        return
    } catch (e: Exception) {
        reportLoadError(BoloPlayerError.UnknownError("DASH MPD 构建失败: ${e.message}", e))
        return
    }
    load(mpd, startPosition)
}

internal fun buildBoloDashMpd(video: BiliDashObject, audio: BiliDashObject?): BoloDashMpd {
    val adaptationSets = buildString {
        append(buildAdaptationSet(video, "video", 1))
        if (audio != null) {
            append('\n')
            append(buildAdaptationSet(audio, "audio", 2))
        }
    }
    val durationSec = maxOf(video.duration, audio?.duration ?: 0L).coerceAtLeast(0L)
    val durationAttribute = mpdDurationAttribute(durationSec)
    val xml = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"$durationAttribute minBufferTime="PT1.5S" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">"""
        )
        appendLine("  <Period id=\"0\">")
        appendLine(adaptationSets)
        appendLine("  </Period>")
        appendLine("</MPD>")
    }
    return BoloDashMpd(
        xml = xml,
        hasAudio = audio != null,
        durationSec = durationSec,
        videoSummary = video.mpdSourceSummary("video"),
        audioSummary = audio?.mpdSourceSummary("audio")
    )
}

private fun buildAdaptationSet(dash: BiliDashObject, contentType: String, id: Int): String {
    val url = dash.baseUrl.ifBlank { dash.backupUrl.firstOrNull().orEmpty() }
    require(url.isNotBlank()) { "DASH $contentType URL 为空" }

    val segmentBase = dash.segmentBase
    val initialization = segmentBase?.resolvedInitialization.orEmpty()
    val indexRange = segmentBase?.resolvedIndexRange.orEmpty()
    require(initialization.isNotBlank()) { "DASH $contentType SegmentBase initialization 为空" }
    require(indexRange.isNotBlank()) { "DASH $contentType SegmentBase indexRange 为空" }

    val mimeType = dash.mimeType.ifBlank { "$contentType/mp4" }
    val representationId = "${contentType}_${dash.quality?.code ?: id}"
    val bandwidth = dash.bandwidth.takeIf { it > 0L } ?: 1L
    val codecAttribute = dash.codecString.takeIf { it.isNotBlank() }?.let { " codecs=\"${it.xmlEscape()}\"" }.orEmpty()
    val videoAttributes = if (contentType == "video") {
        val sizeAttributes = buildString {
            if (dash.width > 0) append(" width=\"${dash.width}\"")
            if (dash.height > 0) append(" height=\"${dash.height}\"")
        }
        val frameRateAttribute = dash.frameRate.takeIf { it.isNotBlank() }?.let { " frameRate=\"${it.xmlEscape()}\"" }.orEmpty()
        sizeAttributes + frameRateAttribute
    } else {
        ""
    }

    return """
            <AdaptationSet id="$id" contentType="$contentType" mimeType="${mimeType.xmlEscape()}" segmentAlignment="true" startWithSAP="1">
              <Representation id="${representationId.xmlEscape()}" bandwidth="$bandwidth"$codecAttribute$videoAttributes>
                <BaseURL>${url.xmlEscape()}</BaseURL>
                <SegmentBase indexRange="${indexRange.xmlEscape()}">
                  <Initialization range="${initialization.xmlEscape()}"/>
                </SegmentBase>
              </Representation>
            </AdaptationSet>
    """.trimIndent().prependIndent("    ")
}

private fun mpdDurationAttribute(durationSec: Long): String =
    if (durationSec > 0L) " mediaPresentationDuration=\"PT${durationSec}S\"" else ""

private fun BiliDashObject.mpdSourceSummary(contentType: String): String {
    val segmentBase = segmentBase
    return "$contentType(" +
        "quality=${quality?.code ?: "unknown"} " +
        "baseUrl=${baseUrl.presenceLabel()} backupCount=${backupUrl.size} " +
        "mime=${mimeType.ifBlank { "empty" }} codec=${codecString.ifBlank { "empty" }} codecEnum=$codec " +
        "bandwidth=$bandwidth durationSec=$duration width=$width height=$height frameRate=${frameRate.ifBlank { "empty" }} " +
        "segmentBase=${segmentBase != null} initialization=${segmentBase?.resolvedInitialization.presenceLabel()} " +
        "indexRange=${segmentBase?.resolvedIndexRange.presenceLabel()}" +
        ")"
}

private fun String?.presenceLabel(): String =
    if (isNullOrBlank()) "empty" else "present"

private fun String.xmlEscape(): String =
    replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
