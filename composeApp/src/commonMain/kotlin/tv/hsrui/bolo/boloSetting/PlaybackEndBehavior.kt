package tv.hsrui.bolo.boloSetting

enum class PlaybackEndBehavior(val storedValue: String, val title: String) {
    Off("off", "关闭"),
    Replay("replay", "重播"),
    NextEpisode("next_episode", "下一集"),
}
