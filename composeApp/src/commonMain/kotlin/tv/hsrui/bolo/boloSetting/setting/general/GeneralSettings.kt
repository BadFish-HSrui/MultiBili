package tv.hsrui.bolo.boloSetting.setting.general

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain
import tv.hsrui.bolo.boloSetting.AppThemeMode

class GeneralSettings(settingsKSafe: KSafePlain) {
    private var storedThemeMode by settingsKSafe("auto", key = "bolo_general_theme_mode")
    private var currentThemeMode by mutableStateOf(
        AppThemeMode.entries.firstOrNull { it.storedValue == storedThemeMode } ?: AppThemeMode.Auto,
    )

    var themeMode: AppThemeMode
        get() = currentThemeMode
        set(value) {
            if (value == currentThemeMode) return
            storedThemeMode = value.storedValue
            currentThemeMode = value
        }

    private var storedClipboardLinkRecognitionEnabled by settingsKSafe(
        false, key = "bolo_general_clipboard_link_recognition_enabled",
    )
    private var currentClipboardLinkRecognitionEnabled by mutableStateOf(storedClipboardLinkRecognitionEnabled)

    var clipboardLinkRecognitionEnabled: Boolean
        get() = currentClipboardLinkRecognitionEnabled
        set(value) {
            if (value == currentClipboardLinkRecognitionEnabled) return
            storedClipboardLinkRecognitionEnabled = value
            currentClipboardLinkRecognitionEnabled = value
        }

    private var storedSystemLinkHandlingEnabled by settingsKSafe(
        false, key = "bolo_general_system_link_handling_enabled",
    )
    private var currentSystemLinkHandlingEnabled by mutableStateOf(storedSystemLinkHandlingEnabled)

    var systemLinkHandlingEnabled: Boolean
        get() = currentSystemLinkHandlingEnabled
        set(value) {
            if (value == currentSystemLinkHandlingEnabled) return
            storedSystemLinkHandlingEnabled = value
            currentSystemLinkHandlingEnabled = value
        }
}
