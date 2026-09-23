package tv.hsrui.bolo.utils.url

expect fun setSystemLinkHandlingEnabled(enabled: Boolean): Boolean

expect fun isSystemLinkHandlingEnabled(): Boolean

internal expect fun openSystemLinkSettings(): Boolean

// 桌面端观察应用窗口之间的焦点转移，避免把关闭应用内弹窗当作切回应用。
internal expect fun observeExternalLinkActivation(onActivation: () -> Unit): () -> Unit
