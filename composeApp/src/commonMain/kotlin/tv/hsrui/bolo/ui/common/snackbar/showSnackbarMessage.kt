package tv.hsrui.bolo.ui.common.snackbar

import org.koin.mp.KoinPlatformTools

fun showSnackbarMessage(message: String, duration: Long = 1500L) {
    val snackbarManager: SnackbarManager =
        KoinPlatformTools.defaultContext().get().get()

    snackbarManager.showMessage(message = message, duration = duration)
}