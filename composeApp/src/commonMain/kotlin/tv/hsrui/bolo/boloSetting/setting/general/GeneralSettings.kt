package tv.hsrui.bolo.boloSetting.setting.general

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import eu.anifantakis.lib.ksafe.KSafePlain
import tv.hsrui.bolo.boloSetting.AppThemeMode
import tv.hsrui.bolo.ui.theme.DefaultColorSpec
import tv.hsrui.bolo.ui.theme.DefaultPaletteStyle
import tv.hsrui.bolo.ui.theme.DefaultSeedColorRgb

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

    private var storedSeedColorRgb by settingsKSafe(
        DefaultSeedColorRgb, key = "bolo_general_seed_color_rgb",
    )
    private var currentSeedColorRgb by mutableStateOf(normalizeSeedColorRgb(storedSeedColorRgb))

    val seedColorRgb: Int
        get() = currentSeedColorRgb

    private var storedColorSpec by settingsKSafe(
        DefaultColorSpec.storedYear(), key = "bolo_general_color_spec",
    )
    private var currentColorSpec by mutableStateOf(
        ColorSpec.SpecVersion.entries.firstOrNull { it.storedYear() == storedColorSpec } ?: DefaultColorSpec,
    )
    private var storedPaletteStyle by settingsKSafe(
        DefaultPaletteStyle.name, key = "bolo_general_palette_style",
    )
    private var currentPaletteStyle by mutableStateOf(
        normalizePaletteStyle(
            PaletteStyle.entries.firstOrNull { it.name == storedPaletteStyle } ?: DefaultPaletteStyle,
            currentColorSpec,
        ),
    )

    val colorSpec: ColorSpec.SpecVersion
        get() = currentColorSpec

    val paletteStyle: PaletteStyle
        get() = currentPaletteStyle

    private var previewSeedColorRgb by mutableStateOf<Int?>(null)
    private var previewColorSpec by mutableStateOf<ColorSpec.SpecVersion?>(null)
    private var previewPaletteStyle by mutableStateOf<PaletteStyle?>(null)

    val effectiveSeedColorRgb: Int get() = previewSeedColorRgb ?: seedColorRgb
    val effectiveColorSpec: ColorSpec.SpecVersion get() = previewColorSpec ?: colorSpec
    val effectivePaletteStyle: PaletteStyle get() = previewPaletteStyle ?: paletteStyle

    fun previewColorSettings(seedColorRgb: Int, colorSpec: ColorSpec.SpecVersion, paletteStyle: PaletteStyle) {
        Snapshot.withMutableSnapshot {
            previewSeedColorRgb = normalizeSeedColorRgb(seedColorRgb)
            previewColorSpec = colorSpec
            previewPaletteStyle = normalizePaletteStyle(paletteStyle, colorSpec)
        }
    }

    fun clearColorPreview() {
        Snapshot.withMutableSnapshot {
            previewSeedColorRgb = null
            previewColorSpec = null
            previewPaletteStyle = null
        }
    }

    fun applyColorSettings(seedColorRgb: Int, colorSpec: ColorSpec.SpecVersion, paletteStyle: PaletteStyle) {
        val normalizedRgb = normalizeSeedColorRgb(seedColorRgb)
        val normalizedStyle = normalizePaletteStyle(paletteStyle, colorSpec)
        if (storedSeedColorRgb != normalizedRgb) storedSeedColorRgb = normalizedRgb
        if (storedColorSpec != colorSpec.storedYear()) storedColorSpec = colorSpec.storedYear()
        if (storedPaletteStyle != normalizedStyle.name) storedPaletteStyle = normalizedStyle.name
        Snapshot.withMutableSnapshot {
            currentSeedColorRgb = normalizedRgb
            currentColorSpec = colorSpec
            currentPaletteStyle = normalizedStyle
            previewSeedColorRgb = null
            previewColorSpec = null
            previewPaletteStyle = null
        }
    }

    private fun normalizeSeedColorRgb(value: Int): Int =
        value.takeIf { it in 0..0xFFFFFF } ?: DefaultSeedColorRgb

    private fun ColorSpec.SpecVersion.storedYear(): Int = when (this) {
        ColorSpec.SpecVersion.SPEC_2021 -> 2021
        ColorSpec.SpecVersion.SPEC_2025 -> 2025
    }

    fun availablePaletteStyles(spec: ColorSpec.SpecVersion): List<PaletteStyle> = when (spec) {
        ColorSpec.SpecVersion.SPEC_2021 -> PaletteStyle.entries
        ColorSpec.SpecVersion.SPEC_2025 -> listOf(
            PaletteStyle.TonalSpot,
            PaletteStyle.Neutral,
            PaletteStyle.Vibrant,
            PaletteStyle.Expressive,
        )
    }

    private fun normalizePaletteStyle(style: PaletteStyle, spec: ColorSpec.SpecVersion): PaletteStyle =
        style.takeIf { it in availablePaletteStyles(spec) } ?: PaletteStyle.TonalSpot

    private var storedSearchSuggestionsEnabled by settingsKSafe(
        true, key = "bolo_general_search_suggestions_enabled",
    )
    private var currentSearchSuggestionsEnabled by mutableStateOf(storedSearchSuggestionsEnabled)

    var searchSuggestionsEnabled: Boolean
        get() = currentSearchSuggestionsEnabled
        set(value) {
            if (value == currentSearchSuggestionsEnabled) return
            storedSearchSuggestionsEnabled = value
            currentSearchSuggestionsEnabled = value
        }

    private var storedSearchTrendingEnabled by settingsKSafe(
        true, key = "bolo_general_search_trending_enabled",
    )
    private var currentSearchTrendingEnabled by mutableStateOf(storedSearchTrendingEnabled)

    var searchTrendingEnabled: Boolean
        get() = currentSearchTrendingEnabled
        set(value) {
            if (value == currentSearchTrendingEnabled) return
            storedSearchTrendingEnabled = value
            currentSearchTrendingEnabled = value
        }

    private var storedDownloadDirectory by settingsKSafe("", key = "bolo_general_download_directory")
    private var currentDownloadDirectory by mutableStateOf(storedDownloadDirectory)

    var downloadDirectory: String
        get() = currentDownloadDirectory
        set(value) {
            if (value == currentDownloadDirectory) return
            storedDownloadDirectory = value
            currentDownloadDirectory = value
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
