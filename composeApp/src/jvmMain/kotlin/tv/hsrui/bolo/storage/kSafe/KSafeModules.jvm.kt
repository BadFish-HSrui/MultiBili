package tv.hsrui.bolo.storage.kSafe

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafePlain
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import tv.hsrui.bolo.storage.desktopStorageDirectory

actual val KSafeModule: Module = module {
    single(named("login")) {
        KSafe(
            fileName = "login",
            baseDir = desktopStorageDirectory()
        )
    }
    single(named("wbi")) {
        KSafe(
            fileName = "wbi",
            baseDir = desktopStorageDirectory()
        )
    }
    single(named("settings")) {
        KSafePlain(
            KSafe(
                fileName = "settings",
                baseDir = desktopStorageDirectory()
            )
        )
    }
    single(named("appData")) {
        KSafePlain(
            KSafe(
                fileName = "app_data",
                baseDir = desktopStorageDirectory()
            )
        )
    }
}
