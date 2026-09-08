package tv.hsrui.bolo.boloSetting

import org.koin.core.qualifier.named
import org.koin.dsl.module

val BoloSettingsModule = module {
    single { BoloSettings(get(named("settings"))) }
}
