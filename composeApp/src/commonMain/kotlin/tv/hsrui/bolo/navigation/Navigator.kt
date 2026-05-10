package tv.hsrui.bolo.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class Navigator {
    val backStack: SnapshotStateList<BoloRoute> = mutableStateListOf(BoloRoute.Main)

    fun navigateTo(route: BoloRoute) {
        backStack.add(route)
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeLast()
        }
    }

    fun goHome() {
        backStack.removeRange(1, backStack.size)
    }
}