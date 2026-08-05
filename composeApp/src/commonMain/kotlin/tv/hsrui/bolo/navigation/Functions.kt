package tv.hsrui.bolo.navigation

import org.koin.mp.KoinPlatformTools
import tv.hsrui.bolo.model.Vid

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
    navigator.navigateTo(BoloRoute.View.FavoriteFolder(mediaId))
}
