package tv.hsrui.bolo.navigation

import org.koin.mp.KoinPlatformTools
import tv.hsrui.bolo.model.Vid

fun openUserSpace(mid: Long) {
    if (mid <= 0) return
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    val route = BoloRoute.UserSpace(mid)
    if (navigator.backStack.lastOrNull() != route) navigator.navigateTo(route)
}

fun openVideo(bvid: String) {
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    navigator.navigateTo(BoloRoute.View.Video(Vid.BVid(bvid)))
}

fun openVideo(avid: Long) {
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    navigator.navigateTo(BoloRoute.View.Video(Vid.AVid(avid)))
}

fun openFavoriteFolder(mediaId: Long) {
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    navigator.navigateTo(BoloRoute.Favorite.Folder(mediaId))
}

fun openMedia(seasonId: Long) {
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    navigator.navigateTo(BoloRoute.View.Media(seasonId))
}
