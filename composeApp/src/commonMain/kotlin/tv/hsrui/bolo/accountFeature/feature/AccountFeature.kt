package tv.hsrui.bolo.accountFeature.feature

import tv.hsrui.bolo.navigation.BoloRoute

enum class AccountFeature(val title: String, val route: BoloRoute) {
    History("历史记录", BoloRoute.AccountFeature.History),
    WatchLater("稍后再看", BoloRoute.AccountFeature.WatchLater),
    Favorite("收藏", BoloRoute.AccountFeature.Favorite)
}