package tv.hsrui.network.wbi

import org.koin.core.qualifier.named
import org.koin.dsl.module

val WbiManagerModule = module {
    single { WbiManager(get(named("wbi"))) }
}