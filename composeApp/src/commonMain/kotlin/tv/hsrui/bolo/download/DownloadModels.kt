package tv.hsrui.bolo.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import tv.hsrui.network.feature.player.BiliDashObject
import tv.hsrui.network.feature.player.VideoSource
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
enum class DownloadType { Video, Media }

@Serializable
data class DownloadGroup(val type: DownloadType, val id: Long) {
    val key: String get() = "${type.name}:$id"
}

data class DownloadTarget(val id: Long, val cid: Long, val subtitle: String, val number: Int) {
    val key: String get() = "$id:$cid"
}

data class DownloadStreams(val video: BiliDashObject, val audio: BiliDashObject?, val spec: DownloadSpec)

fun resolveDownloadStreams(source: VideoSource, spec: DownloadSpec): DownloadStreams {
    check(source.isSuccess) { source.message }
    check(!source.isPreview) { "试看内容不支持完整下载" }
    val videos = source.videoQualities.mapNotNull { quality ->
        val codecs = source.availableVideoCodecs(quality).associateWith { codec ->
            checkNotNull(source.getExactVideo(quality, codec))
        }
        if (codecs.isEmpty()) null else quality to codecs
    }.toMap()
    val audios = source.audioQualities.mapNotNull { quality -> source.getExactAudio(quality)?.let { quality to it } }.toMap()
    check(source.audioQualities.isEmpty() || audios.isNotEmpty()) { "没有可下载的音频规格" }
    // 只在有效流中应用播放器相同的画质、编码和音质回退顺序。
    val available = source.copy(_video = videos, _audio = audios.takeIf { it.isNotEmpty() })
    val video = available.getVideo(spec.videoQuality, spec.videoCodec)
    val audio = available.getAudio(spec.audioQuality)
    val quality = video.quality as? VideoQuality
    check(quality != null && video.getUrls().isNotEmpty() && video.codec != VideoCodec.Audio) { "没有可下载的视频规格" }
    val audioQuality = audio?.quality as? AudioQuality
    check(audio == null || audioQuality != null) { "没有可下载的音频规格" }
    return DownloadStreams(video, audio, DownloadSpec(quality.code, video.codec.code, audioQuality?.code))
}

@Serializable
data class DownloadRequest(
    val id: Long,
    val cid: Long,
    val title: String,
    val spec: DownloadSpec,
    val type: DownloadType = DownloadType.Video,
) {
    val key: String get() = "${type.name}:$id:$cid:${spec.videoQualityCode}:${spec.videoCodecCode}:${spec.audioQualityCode}"
    val fileName: String get() {
        val sanitized = title.map { if (it < ' ' || it in "<>:\"/\\|?*") '_' else it }.joinToString("")
            .ifEmpty { "视频_$id" }
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
    val mainTitle: String = request.title,
    val subtitle: String = "",
    val group: DownloadGroup? = null,
    val episodeNumber: Int = 0,
    val actualSpec: DownloadSpec? = null,
    @Transient val bytesPerSecond: Long = 0,
) {
    val title: String get() = listOf(mainTitle, subtitle).filter(String::isNotBlank).joinToString(" ")
    val fileName: String get() = request.copy(title = title, spec = actualSpec ?: request.spec).fileName
    val episodeKey: String get() = "${request.type.name}:${request.id}:${request.cid}"
    val displayGroupKey: String get() = group?.let { "group:${it.key}" } ?: "task:$id"
}

fun List<DownloadTask>.groupedDownloads(): List<List<DownloadTask>> =
    groupBy { it.displayGroupKey }.values.sortedByDescending { tasks -> tasks.maxOf { it.createdAt } }

fun List<DownloadTask>.orderedDownloadEpisodes(): List<DownloadTask> =
    sortedWith(compareBy<DownloadTask> { it.episodeNumber }.thenBy { it.createdAt }.thenBy { it.id })

fun List<DownloadTask>.completedDownloadEpisodes(): Int =
    groupBy { it.episodeKey }.values.count { episodes -> episodes.all { it.status == DownloadStatus.Completed } }
