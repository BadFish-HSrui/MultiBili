package tv.hsrui.bolo.ui.common.snackbar

import org.koin.dsl.module

val SnackbarModule = module {
    single { SnackbarManager() }
}