package tv.hsrui.bolo.boloSetting.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import tv.hsrui.bolo.navigation.BoloRoute

enum class BoloSetting(val title: String, val icon: ImageVector, val route: BoloRoute) {
    General("通用设置", Icons.Outlined.Settings, route = BoloRoute.BoloSetting.General),
    Playback("播放设置", Icons.Outlined.PlayCircle, route = BoloRoute.BoloSetting.Playback),
    About("关于", Icons.Outlined.Info, route = BoloRoute.BoloSetting.About)
}