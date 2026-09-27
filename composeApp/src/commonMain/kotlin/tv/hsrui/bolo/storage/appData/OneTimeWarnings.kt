package tv.hsrui.bolo.storage.appData

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.anifantakis.lib.ksafe.KSafePlain

class OneTimeWarnings internal constructor(appDataKSafe: KSafePlain) {
    private var storedDynamicLoudnessPending by appDataKSafe(
        true, key = "warning_dynamic_loudness_pending",
    )
    private var currentDynamicLoudnessPending by mutableStateOf(storedDynamicLoudnessPending)

    var dynamicLoudnessPending: Boolean
        get() = currentDynamicLoudnessPending
        set(value) {
            if (value == currentDynamicLoudnessPending) return
            storedDynamicLoudnessPending = value
            currentDynamicLoudnessPending = value
        }
}
