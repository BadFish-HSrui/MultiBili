package tv.hsrui.bolo.navigation

import org.koin.dsl.module

val NavigatorModule = module {
    single { Navigator() }
}