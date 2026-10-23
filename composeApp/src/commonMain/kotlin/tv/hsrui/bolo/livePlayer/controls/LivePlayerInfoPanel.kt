package tv.hsrui.bolo.livePlayer.controls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.livePlayer.base.LivePlayerInfo
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality
import tv.hsrui.network.utils.formatBits
import tv.hsrui.network.utils.formatBytes
import tv.hsrui.network.utils.formatDecimal
import tv.hsrui.network.utils.formatToDuration

@Composable
internal fun LivePlayerInfoPanel(
    info: LivePlayerInfo,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 400.dp).fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("播放信息", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭播放信息", modifier = Modifier.size(20.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LivePlayerInfoRow("当前画质", info.currentQn?.let { LiveVideoQuality.fromCode(it)?.title ?: it.toString() })
                val format = when (info.formatName) {
                    "fmp4" -> "HLS / fMP4"
                    "ts" -> "HLS / TS"
                    "flv" -> "FLV"
                    else -> listOfNotNull(info.protocolName, info.formatName).joinToString(" / ").ifEmpty { null }
                }
                LivePlayerInfoRow("传输格式", format)
                LivePlayerInfoRow("播放码率", info.totalPlaybackBitrateBps?.let { "${it.formatBits()}ps" })
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                val media = info.media
                val video = media.video
                LivePlayerInfoRow("视频编码", video.codec)
                LivePlayerInfoRow("播放码率", video.playbackBitrateBps?.let { "${it.formatBits()}ps" })
                val resolution = if (video.width != null && video.height != null) "${video.width}×${video.height}" else "—"
                LivePlayerInfoRow("视频分辨率", "$resolution·${video.fps?.formatDecimal() ?: "—"}fps")
                LivePlayerInfoRow("视频解码器", video.decoderDescription)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                val audio = media.audio
                LivePlayerInfoRow("音频编码", audio?.codec)
                LivePlayerInfoRow("播放码率", audio?.playbackBitrateBps?.let { "${it.formatBits()}ps" })
                LivePlayerInfoRow("采样率", audio?.sampleRateHz?.let { "$it Hz" })
                val originalLayout = audio?.channelLayout
                val outputLayout = audio?.outputChannelLayout
                val layout = if (originalLayout != null && outputLayout != null && originalLayout != outputLayout) {
                    "${liveInfoChannelLayout(originalLayout)} >>> ${liveInfoChannelLayout(outputLayout)}"
                } else liveInfoChannelLayout(originalLayout)
                val merged = audio?.channelsMerged == true && outputLayout != null && (audio.outputChannelCount ?: 0) > 1
                LivePlayerInfoRow("声道布局", if (merged && layout != null) "$layout（已合并）" else layout)
                LivePlayerInfoRow("音频解码器", audio?.decoderDescription)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                LivePlayerInfoRow("解码丢帧", liveInfoDroppedFrames(media.decoderDroppedFrames, media.decoderDroppedFramesPerSecond))
                LivePlayerInfoRow("输出丢帧", liveInfoDroppedFrames(media.outputDroppedFrames, media.outputDroppedFramesPerSecond))
                LivePlayerInfoRow("音画偏差", media.avSyncDifferenceMs?.let(::liveInfoAvSync))
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                LivePlayerInfoRow("硬解路径", if (media.hardwareDecoder == "no") "软件解码" else media.hardwareDecoder)
                LivePlayerInfoRow("硬解互操作", media.hardwareInterop)
                LivePlayerInfoRow("视频输出", media.videoOutput)
                LivePlayerInfoRow("音频输出", media.audioOutput)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                LivePlayerInfoRow("下载速度", info.downloadBytesPerSecond?.let { "${it.formatBytes()}/s" }, singleLine = true)
                LivePlayerInfoRow("预载大小", info.cachedBytes?.formatBytes(), singleLine = true)
                LivePlayerInfoRow("下载总量", info.receivedBytes?.formatBytes(), singleLine = true)
                LivePlayerInfoRow("缓存时长", info.cachedDurationMs?.let { (it / 1000).formatToDuration() })
                LivePlayerInfoRow("播放位置", info.liveOffsetMs?.let { "-${(it / 1000).formatToDuration()}" })
            }
        }
    }
}

@Composable
private fun LivePlayerInfoRow(label: String, value: String?, singleLine: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            text = value ?: "—",
            modifier = Modifier.weight(1f).then(if (singleLine) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        )
    }
}

private fun liveInfoChannelLayout(layout: String?): String? = when (layout) {
    "mono" -> "单声道"
    "stereo" -> "双声道"
    else -> layout
}

private fun liveInfoDroppedFrames(count: Long?, rate: Double?): String? {
    if (count == null) return null
    return "${rate?.formatDecimal(1) ?: "—"}/s（$count）"
}

private fun liveInfoAvSync(value: Double): String {
    val milliseconds = (-value).formatDecimal(0)
    return "${if (value < 0 && milliseconds != "0") "+" else ""}${milliseconds}ms"
}
