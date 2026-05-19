package tv.hsrui.bolo.boloSetting.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.vector.ImageVector
import tv.hsrui.bolo.navigation.BoloRoute

enum class BoloSetting(val title: String, val icon: ImageVector, val route: BoloRoute) {
    About("关于", Icons.Outlined.Info, route = BoloRoute.BoloSetting.About)
}