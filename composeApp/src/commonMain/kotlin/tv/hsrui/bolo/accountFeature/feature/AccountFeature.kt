package tv.hsrui.bolo.accountFeature.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.ui.graphics.vector.ImageVector
import tv.hsrui.bolo.navigation.BoloRoute

enum class AccountFeature(val title: String, val icon: ImageVector, val route: BoloRoute) {
    History("历史记录", Icons.Rounded.History, BoloRoute.AccountFeature.History),
    Favorite("我的收藏", Icons.Rounded.StarOutline, BoloRoute.Favorite.List),
    WatchLater("稍后再看", Icons.Outlined.WatchLater, BoloRoute.AccountFeature.WatchLater),
    Download("下载管理", Icons.Rounded.Download, BoloRoute.AccountFeature.Download),
}
