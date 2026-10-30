package tv.hsrui.network.liveFeature.player

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.BOLO_UA
import tv.hsrui.network.constant.LiveApiUrls
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoCodec
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality
import tv.hsrui.network.wbi.buildWithWbi

// 多画质清单可能重定向到媒体 CDN，不能携带 API 客户端的账号 Cookie。
private val liveMasterClient by lazy { HttpClient() }

suspend fun fetchLivePlayInfo(
    roomId: Long,
    qn: Int = LiveVideoQuality.ORIGINAL.code,
): LivePlayInfoData = withTimeout(15_000) {
    require(roomId > 0) { "直播间 ID 无效" }
    require(qn > 0) { "直播画质无效" }
    ApiClient.httpClient.get(LiveApiUrls.BASE + LiveApiUrls.PLAY_INFO) {
        buildWithWbi {
            parameter("room_id", roomId)
            parameter("qn", qn)
            parameter("no_playurl", 0)
            parameter("protocol", "0,1")
            parameter("format", "0,1,2")
            parameter("codec", LiveVideoCodec.entries.joinToString(",") { it.code.toString() })
            parameter("platform", "web")
            parameter("ptype", 8)
            parameter("dolby", 5)
            parameter("panorama", 1)
            parameter("eotf", "0,1,2")
        }
        header("Referer", "https://live.bilibili.com/$roomId")
    }.body<RawLivePlayResponse>().toLivePlayInfo()
}

/** 返回服务端候选画质；不额外请求多画质清单，也不代表设备解码能力。 */
suspend fun fetchLiveAvailableQns(roomId: Long): List<Int> =
    fetchLivePlayInfo(roomId).availableQns

/** 展开当前编码的多画质清单；返回地址的 currentQn 均来自清单中的实际档位。 */
suspend fun fetchLiveHlsStreams(
    roomId: Long,
    stream: LivePlayStreamData,
): List<LivePlayStreamData> = withTimeout(15_000) {
    require(roomId > 0) { "直播间 ID 无效" }
    if (stream.protocolName != "http_hls" || stream.formatName != "fmp4") return@withTimeout emptyList()
    val masterUrl = stream.masterUrl?.takeIf(String::isNotBlank) ?: return@withTimeout emptyList()
    val streamName = stream.urls.firstNotNullOfOrNull {
        Regex("""/live-bvc/\d+/(live_[^/.]+)[/.]""").find(it)?.groupValues?.get(1)
    } ?: return@withTimeout emptyList()
    val parsed = Url(masterUrl)
    require(parsed.protocol == URLProtocol.HTTPS || parsed.protocol == URLProtocol.HTTP) { "直播清单地址无效" }
    liveMasterClient.prepareGet(masterUrl) {
        parameter("stream_name", streamName)
        header("User-Agent", BOLO_UA)
        header("Referer", "https://live.bilibili.com/$roomId")
    }.execute { response ->
        check(response.status.isSuccess()) { "直播画质清单请求失败：HTTP ${response.status.value}" }
        val limit = 1024 * 1024
        val bytes = response.bodyAsChannel().readRemaining((limit + 1).toLong()).readByteArray()
        check(bytes.isNotEmpty() && bytes.size <= limit) { "直播画质清单大小无效" }
        parseLiveHlsStreams(bytes.decodeToString(), response.call.request.url.toString(), stream)
    }
}
