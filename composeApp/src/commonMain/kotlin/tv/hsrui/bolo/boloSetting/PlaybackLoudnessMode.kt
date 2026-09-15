package tv.hsrui.bolo.boloSetting

import tv.hsrui.network.feature.player.VideoLoudnessData

enum class PlaybackLoudnessMode(val storedValue: String, val title: String) {
    Standard("standard", "标准"),
    HighDynamic("high_dynamic", "高动态"),
    Off("off", "关闭");

    fun gainDb(loudness: VideoLoudnessData?): Double {
        if (this == Off || loudness == null ||
            loudness.measuredLoudnessLufs < loudness.lowLoudnessThresholdLufs) return 0.0
        val target = when (this) {
            Standard -> loudness.standardTargetLoudnessLufs
            HighDynamic -> loudness.highDynamicTargetLoudnessLufs
            Off -> return 0.0
        }
        // 不裁剪有效补偿（阈值处可能需要 +14 dB）；异常值不送入 mpv。
        return (target - loudness.measuredLoudnessLufs).takeIf { it.isFinite() && it in -150.0..150.0 } ?: 0.0
    }
}
