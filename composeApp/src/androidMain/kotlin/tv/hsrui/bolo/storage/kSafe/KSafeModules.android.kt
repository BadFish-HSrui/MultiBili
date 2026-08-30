package tv.hsrui.bolo.storage.kSafe

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual val KSafeModule: Module = module {
    single(named("login")) {
        KSafe(
            context = androidApplication(),
            fileName = "login"
        )
    }
    single(named("wbi")) {
        KSafe(
            context = androidApplication(),
            fileName = "wbi"
        )
    }
    single(named("appData")) {
        KSafe(
            context = androidApplication(),
            fileName = "app_data"
        )
    }
}
