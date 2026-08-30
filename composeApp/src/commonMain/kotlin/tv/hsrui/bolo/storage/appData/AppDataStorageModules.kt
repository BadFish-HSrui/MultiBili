package tv.hsrui.bolo.storage.appData

import org.koin.core.qualifier.named
import org.koin.dsl.module

val AppDataStorageModule = module {
    single { AppDataStorage(get(named("appData"))) }
}
