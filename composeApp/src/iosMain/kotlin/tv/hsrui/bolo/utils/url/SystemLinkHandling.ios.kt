package tv.hsrui.bolo.utils.url

actual fun setSystemLinkHandlingEnabled(enabled: Boolean): Boolean = !enabled

actual fun isSystemLinkHandlingEnabled(): Boolean = false

internal actual fun openSystemLinkSettings(): Boolean = false

internal actual fun observeExternalLinkActivation(onActivation: () -> Unit): () -> Unit = {}
