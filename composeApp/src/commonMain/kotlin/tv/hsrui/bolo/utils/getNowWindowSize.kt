package tv.hsrui.bolo.utils

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import tv.hsrui.bolo.getPlatform

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
        ) -> if (getPlatform().deviceCode.contains("iPhone")) AppWindowSize.MEDIUM
        else AppWindowSize.EXPANDED

        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        ) -> AppWindowSize.MEDIUM

        else -> AppWindowSize.COMPACT
    }
}

@Composable
fun isCompact() = getNowWindowSize() == AppWindowSize.COMPACT

@Composable
fun isMedium() = getNowWindowSize() == AppWindowSize.MEDIUM

@Composable
fun isExpanded() = getNowWindowSize() == AppWindowSize.EXPANDED