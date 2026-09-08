package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tv.hsrui.network.feature.player.BiliDashObject

internal data class BoloDashMpd(
    val xml: String,
    val hasAudio: Boolean,
    val durationMs: Long,
    val videoSummary: String,
    val audioSummary: String?
)

suspend fun BoloPlayerController.load(
    video: BiliDashObject,
    audio: BiliDashObject? = null,
    startPositionMs: Long = 0L
) {
    val mpd = try {
        buildBoloDashMpd(video, audio)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        reportLoadError(BoloPlayerError.FormatNotSupported(e.message ?: "DASH MPD 构建参数无效"))
        return
    } catch (e: Exception) {
        reportLoadError(BoloPlayerError.UnknownError("DASH MPD 构建失败: ${e.message}", e))
        return
    }
    load(mpd, startPositionMs)
}

internal suspend fun buildBoloDashMpd(video: BiliDashObject, audio: BiliDashObject?): BoloDashMpd = coroutineScope {
    val videoIndexRequest = async { resolveDashIndex(video) }
    val audioIndexRequest = audio?.let { async { resolveDashIndex(it) } }
    val videoIndex = videoIndexRequest.await()
    val audioIndex = audioIndexRequest?.await()
    val adaptationSets = buildString {
        append(buildAdaptationSet(video, videoIndex, "video", 1))
        if (audio != null && audioIndex != null) {
            append('\n')
            append(buildAdaptationSet(audio, audioIndex, "audio", 2))
        }
    }
    val durationMs = maxOf(videoIndex.durationMs, audioIndex?.durationMs ?: 0L)
    val durationAttribute = mpdDurationAttribute(durationMs)
    val xml = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static"$durationAttribute minBufferTime="PT1.5S" profiles="urn:mpeg:dash:profile:isoff-main:2011">"""
        )
        appendLine("  <Period id=\"0\">")
        appendLine(adaptationSets)
        appendLine("  </Period>")
        appendLine("</MPD>")
    }
    BoloDashMpd(
        xml = xml,
        hasAudio = audio != null,
        durationMs = durationMs,
        videoSummary = video.mpdSourceSummary("video"),
        audioSummary = audio?.mpdSourceSummary("audio")
    )
}

private fun buildAdaptationSet(dash: BiliDashObject, index: DashIndex, contentType: String, id: Int): String {
    val initialization = dash.segmentBase?.resolvedInitialization.orEmpty()
    val initRange = parseDashRange(initialization)
    require(initRange.last < index.resourceLength) { "DASH 初始化范围超出媒体长度" }
    val timeline = index.segments.joinToString("\n") { "<S t=\"${it.time}\" d=\"${it.duration}\"/>" }
    val segments = index.segments.joinToString("\n") {
        "<SegmentURL mediaRange=\"${it.start}-${it.endInclusive}\"/>"
    }
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
                <BaseURL>${index.url.xmlEscape()}</BaseURL>
                <SegmentList timescale="${index.timescale}" presentationTimeOffset="${index.firstTime}">
                  <Initialization range="${initialization.xmlEscape()}"/>
                  <SegmentTimeline>
                    $timeline
                  </SegmentTimeline>
                  $segments
                </SegmentList>
              </Representation>
            </AdaptationSet>
    """.trimIndent().prependIndent("    ")
}

private fun mpdDurationAttribute(durationMs: Long): String {
    if (durationMs <= 0L) return ""

    val wholeSeconds = durationMs / 1_000L
    val remainderMs = durationMs % 1_000L
    val durationValue = if (remainderMs == 0L) {
        wholeSeconds.toString()
    } else {
        "$wholeSeconds.${remainderMs.toString().padStart(3, '0').trimEnd('0')}"
    }
    return " mediaPresentationDuration=\"PT${durationValue}S\""
}

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
