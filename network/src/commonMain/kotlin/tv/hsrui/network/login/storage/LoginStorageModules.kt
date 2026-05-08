package tv.hsrui.network.login.storage

import org.koin.core.qualifier.named
import org.koin.dsl.module

val LoginStorageModule = module {
    single { LoginStorage(get(named("login"))) }
}