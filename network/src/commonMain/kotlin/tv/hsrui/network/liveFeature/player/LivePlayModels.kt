package tv.hsrui.network.liveFeature.player

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.takeFrom
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoCodec
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality

data class LivePlayInfoData(
    val roomId: Long,
    val isLive: Boolean,
    val streams: List<LivePlayStreamData>,
) {
    val availableQns: List<Int>
        get() = streams.flatMap { it.availableQns }.distinct().sortedDescending()
}

data class LivePlayStreamData(
    val protocolName: String,
    val formatName: String,
    val codec: LiveVideoCodec,
    val currentQn: Int,
    val availableQns: List<Int>,
    /** 这些地址只对应 currentQn，不能用于代表其他候选画质。 */
    val urls: List<String>,
    /** 多画质清单入口；后续取流时还需结合当前编码的流名称。 */
    val masterUrl: String?,
    val hdrType: Int,
    val isDrm: Boolean,
) {
    val quality: LiveVideoQuality?
        get() = LiveVideoQuality.fromCode(currentQn)
}

internal fun RawLivePlayResponse.toLivePlayInfo(): LivePlayInfoData {
    check(code == 0) { "直播播放信息获取失败（$code）：$message" }
    val room = checkNotNull(data) { "直播播放信息为空" }
    val isLive = room.liveStatus == 1
    val playUrl = room.playUrlInfo?.playUrl
    check(!isLive || playUrl != null) { "直播播放信息不可用" }
    val streams = buildList {
        for (protocol in playUrl?.streams.orEmpty()) {
            for (format in protocol.formats) {
                for (rawCodec in format.codecs) {
                    val codec = LiveVideoCodec.fromName(rawCodec.codecName) ?: continue
                    val urls = if (rawCodec.baseUrl.isBlank()) emptyList() else rawCodec.urlInfo
                        .filter { it.host.isNotBlank() }
                        .map { it.host + rawCodec.baseUrl + it.extra }
                        .distinct()
                    val masterUrl = format.masterUrl?.takeIf(String::isNotBlank)
                    if (urls.isEmpty() && masterUrl == null) continue
                    add(
                        LivePlayStreamData(
                            protocolName = protocol.protocolName,
                            formatName = format.formatName,
                            codec = codec,
                            currentQn = rawCodec.currentQn,
                            availableQns = rawCodec.acceptQn.filter { it > 0 }.distinct().sortedDescending(),
                            urls = urls,
                            masterUrl = masterUrl,
                            hdrType = rawCodec.hdrType,
                            isDrm = rawCodec.isDrm,
                        )
                    )
                }
            }
        }
    }
    return LivePlayInfoData(roomId = room.roomId, isLive = isLive, streams = streams)
}

internal fun parseLiveHlsStreams(
    content: String,
    masterUrl: String,
    stream: LivePlayStreamData,
): List<LivePlayStreamData> {
    val lines = content.removePrefix("\uFEFF").lineSequence().map(String::trim).filter(String::isNotEmpty).iterator()
    check(lines.hasNext() && lines.next() == "#EXTM3U") { "直播画质清单格式无效" }
    val attributePattern = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")
    val urlsByQn = linkedMapOf<Int, MutableList<String>>()
    var pendingQn: Int? = null
    for (line in lines) {
        if (line.startsWith("#EXT-X-STREAM-INF:")) {
            val attributes = attributePattern.findAll(line.substringAfter(':')).associate {
                it.groupValues[1] to it.groupValues[2].removeSurrounding("\"")
            }
            val codecs = attributes["CODECS"].orEmpty().split(',').mapNotNull { value ->
                when (value.trim().substringBefore('.').lowercase()) {
                    "avc1", "avc3" -> LiveVideoCodec.AVC
                    "hev1", "hvc1" -> LiveVideoCodec.HEVC
                    "av01" -> LiveVideoCodec.AV1
                    else -> null
                }
            }.toSet()
            pendingQn = attributes["BILI-QN"]?.toIntOrNull()?.takeIf { it > 0 && codecs == setOf(stream.codec) }
        } else if (!line.startsWith('#')) {
            val qn = pendingQn
            pendingQn = null
            if (qn == null) continue
            val url = runCatching {
                val resolved = if (line.startsWith("https://") || line.startsWith("http://")) line
                    else URLBuilder(masterUrl).apply {
                        parameters.clear()
                        fragment = ""
                    }.takeFrom(line).buildString()
                val parsed = Url(resolved)
                resolved.takeIf { parsed.protocol == URLProtocol.HTTPS || parsed.protocol == URLProtocol.HTTP }
            }.getOrNull() ?: continue
            val urls = urlsByQn.getOrPut(qn) { mutableListOf() }
            if (url !in urls) urls += url
        }
    }
    return urlsByQn.map { (qn, urls) ->
        stream.copy(currentQn = qn, availableQns = listOf(qn), urls = urls)
    }
}
