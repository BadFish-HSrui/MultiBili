package tv.hsrui.bolo.boloSetting.setting.live

enum class LiveHistoryReportMode(val storedValue: String, val title: String) {
    Off("off", "关闭"),
    OnEnter("on_enter", "进入时"),
    OnExit("on_exit", "退出时"),
}
