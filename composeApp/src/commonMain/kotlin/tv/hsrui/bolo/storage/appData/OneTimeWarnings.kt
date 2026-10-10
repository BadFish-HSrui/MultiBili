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

    private var storedCheck114514 by appDataKSafe(
        true, key = "hidden_easter_egg_114514_pending",
    )
    private var currentCheck114514 by mutableStateOf(storedCheck114514)

    var Check114514: Boolean
        get() = currentCheck114514
        set(value) {
            if (value == currentCheck114514) return
            storedCheck114514 = value
            currentCheck114514 = value
        }
}
