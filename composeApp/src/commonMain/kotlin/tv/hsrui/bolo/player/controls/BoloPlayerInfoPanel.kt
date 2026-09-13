package tv.hsrui.bolo.player.controls

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
import tv.hsrui.bolo.player.base.BoloPlayerInfo
import kotlin.math.roundToLong

@Composable
internal fun BoloPlayerInfoPanel(
    info: BoloPlayerInfo,
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
                val video = info.video
                BoloPlayerInfoRow("视频编码", video.codec)
                BoloPlayerInfoRow("标称码率", formatPlayerBitrate(video.nominalBitrateBps))
                BoloPlayerInfoRow("播放码率", formatPlayerBitrate(video.playbackBitrateBps))
                val resolution = if (video.width != null && video.height != null) "${video.width}×${video.height}" else "—"
                BoloPlayerInfoRow("视频分辨率", "$resolution·${video.fps?.let(::playerInfoDecimal) ?: "—"}fps")
                BoloPlayerInfoRow("播放分片", playerInfoFragment(video.fragmentIndex, video.fragmentCount))
                BoloPlayerInfoRow("视频解码器", playerInfoDecoder(video.decoder, video.decoderDescription))
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                val audio = info.audio
                BoloPlayerInfoRow("音频编码", audio?.codec)
                BoloPlayerInfoRow("标称码率", formatPlayerBitrate(audio?.nominalBitrateBps))
                BoloPlayerInfoRow("播放码率", formatPlayerBitrate(audio?.playbackBitrateBps))
                BoloPlayerInfoRow("采样率", audio?.sampleRateHz?.let { "$it Hz" })
                val originalLayout = audio?.channelLayout
                val outputLayout = audio?.outputChannelLayout
                val layout = if (originalLayout != null && outputLayout != null && originalLayout != outputLayout) {
                    "${playerInfoChannelLayout(originalLayout)} >>> ${playerInfoChannelLayout(outputLayout)}"
                } else {
                    playerInfoChannelLayout(originalLayout)
                }
                val merged = audio?.channelsMerged == true && outputLayout != null &&
                    (audio.outputChannelCount ?: 0) > 1
                BoloPlayerInfoRow("声道布局", if (merged && layout != null) "$layout（已合并）" else layout)
                BoloPlayerInfoRow("播放分片", playerInfoFragment(audio?.fragmentIndex, audio?.fragmentCount))
                BoloPlayerInfoRow("音频解码器", playerInfoDecoder(audio?.decoder, audio?.decoderDescription))
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                BoloPlayerInfoRow("硬解路径", if (info.hardwareDecoder == "no") "软件解码" else info.hardwareDecoder)
                BoloPlayerInfoRow("硬解互操作", info.hardwareInterop)
                BoloPlayerInfoRow("视频输出", info.videoOutput)
                BoloPlayerInfoRow("音频输出", info.audioOutput)
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.2f))
                BoloPlayerInfoRow("视频下载速度", formatPlayerDownloadSpeed(video.downloadBytesPerSecond))
                BoloPlayerInfoRow("音频下载速度", formatPlayerDownloadSpeed(audio?.downloadBytesPerSecond))
                BoloPlayerInfoRow("合计下载速度", formatPlayerDownloadSpeed(info.downloadBytesPerSecond))
            }
        }
    }
}

@Composable
private fun BoloPlayerInfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            text = value ?: "—",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun playerInfoChannelLayout(layout: String?): String? = when (layout) {
    "mono" -> "单声道"
    "stereo" -> "双声道"
    else -> layout
}

private fun playerInfoFragment(index: Int?, count: Int?): String =
    if (index == null && count == null) "—" else "${index ?: "—"} / ${count ?: "—"}"

private fun playerInfoDecoder(name: String?, description: String?): String? =
    listOfNotNull(name, description).distinct().takeIf { it.isNotEmpty() }?.joinToString("\n")

private fun playerInfoDecimal(value: Double): String {
    val scaled = (value * 100).roundToLong()
    val fraction = (scaled % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${scaled / 100}" else "${scaled / 100}.$fraction"
}

private fun formatPlayerBitrate(value: Long?): String = when {
    value == null -> "—"
    value >= 1_000_000 -> "${playerInfoDecimal(value / 1_000_000.0)} Mbps"
    else -> "${playerInfoDecimal(value / 1_000.0)} kbps"
}

internal fun formatPlayerDownloadSpeed(value: Long?): String = when {
    value == null -> "—"
    value >= 1_048_576 -> "${playerInfoDecimal(value / 1_048_576.0)} MiB/s"
    value >= 1_024 -> "${playerInfoDecimal(value / 1_024.0)} KiB/s"
    else -> "$value B/s"
}
