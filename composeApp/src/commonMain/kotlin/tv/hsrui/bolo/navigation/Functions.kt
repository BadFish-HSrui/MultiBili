package tv.hsrui.bolo.navigation

import org.koin.mp.KoinPlatformTools
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.view.video.VideoPlaybackRequest

fun openVideoList(request: VideoPlaybackRequest.VideoList) {
    if (!request.isValid) return
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    val route = BoloRoute.View.VideoList(request)
    if (navigator.backStack.lastOrNull() != route) navigator.navigateTo(route)
}

fun openUserSpace(mid: Long) {
    if (mid <= 0) return
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    val route = BoloRoute.User.Space(mid)
    if (navigator.backStack.lastOrNull() != route) navigator.navigateTo(route)
}

fun openUserCollection(mid: Long, seasonId: Long) {
    if (mid <= 0 || seasonId <= 0) return
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    val route = BoloRoute.User.Collection(mid, seasonId)
    if (navigator.backStack.lastOrNull() != route) navigator.navigateTo(route)
}

fun openUserSeries(mid: Long, seriesId: Long) {
    if (mid <= 0 || seriesId <= 0) return
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    val route = BoloRoute.User.Series(mid, seriesId)
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
