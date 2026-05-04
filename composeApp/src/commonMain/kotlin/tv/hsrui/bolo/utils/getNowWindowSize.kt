package tv.hsrui.bolo.utils

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

enum class AppWindowSize {
    EXPANDED,
    MEDIUM,
    COMPACT
}

@Composable
fun getNowWindowSize(): AppWindowSize {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        ) -> AppWindowSize.EXPANDED

        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        ) -> AppWindowSize.MEDIUM

        else -> AppWindowSize.COMPACT
    }
}