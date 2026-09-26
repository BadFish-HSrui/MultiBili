package tv.hsrui.bolo.boloSetting

import eu.anifantakis.lib.ksafe.KSafePlain
import tv.hsrui.bolo.boloSetting.setting.general.GeneralSettings
import tv.hsrui.bolo.boloSetting.setting.playback.PlaybackSettings

class BoloSettings(settingsKSafe: KSafePlain) {
    val general = GeneralSettings(settingsKSafe)
    val playback = PlaybackSettings(settingsKSafe)
}
