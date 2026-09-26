package tv.hsrui.bolo.boloSetting

import org.koin.core.qualifier.named
import org.koin.dsl.module
import tv.hsrui.bolo.player.settings.BoloPlayerSettings

val BoloSettingsModule = module {
    single { BoloSettings(get(named("settings"))) }
    single { BoloPlayerSettings(get(named("settings"))) }
}
