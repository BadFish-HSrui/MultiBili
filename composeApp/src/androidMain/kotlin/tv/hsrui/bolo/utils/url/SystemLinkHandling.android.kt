package tv.hsrui.bolo.utils.url

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

private fun externalLinkComponent() = ComponentName(AppContext.instance, "tv.hsrui.bolo.ExternalLinkEntry")

actual fun setSystemLinkHandlingEnabled(enabled: Boolean): Boolean = runCatching {
    if (isSystemLinkHandlingEnabled() != enabled) {
        AppContext.instance.packageManager.setComponentEnabledSetting(
            externalLinkComponent(),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
    isSystemLinkHandlingEnabled() == enabled
}.getOrDefault(false)

actual fun isSystemLinkHandlingEnabled(): Boolean = runCatching {
    AppContext.instance.packageManager.getComponentEnabledSetting(externalLinkComponent()) ==
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}.getOrDefault(false)

internal actual fun observeExternalLinkActivation(onActivation: () -> Unit): () -> Unit = {}

internal actual fun openSystemLinkSettings(): Boolean {
    val context = AppContext.instance
    val actions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    } else {
        listOf(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }
    return actions.any { action ->
        runCatching {
            context.startActivity(
                Intent(action, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
