package tv.hsrui.bolo.boloSetting

enum class PlaybackProgressReportMode(val storedValue: String, val title: String) {
    Off("off", "关闭"),
    EveryMinute("every_minute", "每分钟"),
    OnExit("on_exit", "退出时"),
}
