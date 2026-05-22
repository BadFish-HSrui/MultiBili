package tv.hsrui.bolo.navigation

import org.koin.mp.KoinPlatformTools

fun openVideo(bvid: String) {
    val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
    navigator.navigateTo(BoloRoute.View.VideoBV(bvid))
}