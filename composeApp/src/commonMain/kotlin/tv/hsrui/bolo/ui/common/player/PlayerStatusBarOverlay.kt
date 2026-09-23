package tv.hsrui.bolo.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.common.systembar.LightSystemBarContentEffect

@Composable
fun PlayerStatusBarOverlay(isFullscreen: Boolean) {
    if (getPlatform().type == PlatformType.Desktop) return

    LightSystemBarContentEffect()
    if (isFullscreen) return

    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.safeDrawing)
            .background(Color.Black)
    )
}
