package tv.hsrui.bolo.download

import kotlinx.serialization.Serializable
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
import tv.hsrui.network.feature.player.enumModels.VideoQuality

@Serializable
data class DownloadSpec(val videoQualityCode: Int, val videoCodecCode: Int, val audioQualityCode: Int?) {
    val videoQuality: VideoQuality get() = VideoQuality.entries.first { it.code == videoQualityCode }
    val videoCodec: VideoCodec get() = VideoCodec.entries.first { it.code == videoCodecCode }
    val audioQuality: AudioQuality? get() = audioQualityCode?.let { code -> AudioQuality.entries.first { it.code == code } }
    val label: String get() = "${videoQuality.shortTitle}_${videoCodec.name}_${audioQuality?.shortTitle ?: "无音轨"}"
}

@Serializable
data class DownloadRequest(val avid: Long, val cid: Long, val title: String, val spec: DownloadSpec) {
    val key: String get() = "$avid:$cid:${spec.videoQualityCode}:${spec.videoCodecCode}:${spec.audioQualityCode}"
    val fileName: String get() {
        val sanitized = title.map { if (it < ' ' || it in "<>:\"/\\|?*") '_' else it }.joinToString("")
            .ifEmpty { "视频_$avid" }
        val clean = if (Regex("(?i)^(con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])\\.").containsMatchIn(sanitized)) "_$sanitized" else sanitized
        val suffix = "_${spec.label}.mp4"
        var end = clean.length
        while ((clean.substring(0, end) + suffix).encodeToByteArray().size > 220) {
            end--
            if (end > 0 && clean[end - 1].isHighSurrogate()) end--
        }
        return clean.substring(0, end) + suffix
    }
}

@Serializable
enum class DownloadStatus {
    Queued, Downloading, WaitingForMerge, Merging, Saving, Completed, Failed, Canceled;

    val isTerminal: Boolean get() = this == Completed || this == Failed || this == Canceled
    val title: String get() = when (this) {
        Queued -> "等待下载"
        Downloading -> "下载中"
        WaitingForMerge -> "等待合成"
        Merging -> "合成中"
        Saving -> "保存中"
        Completed -> "已完成"
        Failed -> "下载失败"
        Canceled -> "已取消"
    }
}

@Serializable
data class DownloadOutput(val fileName: String, val location: String, val size: Long)

@Serializable
data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val createdAt: Long,
    val updatedAt: Long,
    val status: DownloadStatus = DownloadStatus.Queued,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val mergeProgress: Float = 0f,
    val error: String? = null,
    val output: DownloadOutput? = null,
)
