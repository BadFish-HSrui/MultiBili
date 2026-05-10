package tv.hsrui.bolo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.notosans_bold
import multibili.composeapp.generated.resources.notosans_regular
import org.jetbrains.compose.resources.Font

@Composable
fun rememberAppTypography(): Typography {
    val notoSansFamily = FontFamily(
        Font(Res.font.notosans_regular, FontWeight.Normal),
        Font(Res.font.notosans_bold, FontWeight.Bold),
    )

    val bodyFontFamily = notoSansFamily
    val displayFontFamily = notoSansFamily
    val baseline = Typography()

    return remember(notoSansFamily) {
        Typography(
            displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily),
            displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily),
            displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily),
            headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily),
            headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily),
            headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily),
            titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily),
            titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily),
            titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily),
            bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
            bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
            bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
            labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
            labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
            labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily
            ),
        )
    }
}