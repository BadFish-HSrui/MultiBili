package tv.hsrui.bolo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicMaterialThemeState
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color = SeedColor,
    specVersion: ColorSpec.SpecVersion = DefaultColorSpec,
    style: PaletteStyle = DefaultPaletteStyle,
    content: @Composable () -> Unit,
) {
    val dynamicThemeState = rememberDynamicMaterialThemeState(
        isDark = isDarkTheme,
        style = style,
        seedColor = seedColor,
        specVersion = specVersion,
    )

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = when(getPlatform().type) {
                PlatformType.Ios -> 1.08F
                PlatformType.Android -> 1F
                PlatformType.Desktop -> 1.1F
            }
        )
    ) {
        DynamicMaterialExpressiveTheme(
            state = dynamicThemeState,
            motionScheme = MotionScheme.expressive(),
            animate = true,
            content = content,
            typography = rememberAppTypography()
        )
    }
}
