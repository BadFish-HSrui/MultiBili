package tv.hsrui.bolo.storage.kSafe

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual val KSafeModule: Module = module {
    single(named("login")) {
        KSafe(
            fileName = "login"
        )
    }
    single(named("wbi")) {
        KSafe(
            fileName = "wbi"
        )
    }
    single(named("appData")) {
        KSafe(
            fileName = "app_data"
        )
    }
}
