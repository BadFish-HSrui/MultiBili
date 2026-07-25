package tv.hsrui.bolo.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class Navigator {
    val backStack: SnapshotStateList<BoloRoute> = mutableStateListOf(BoloRoute.Debug)
    val currentDepth: Int get() = (backStack.size - 1)
    fun navigateTo(route: BoloRoute) {
        backStack.add(route)
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeLast()
        }
    }

    fun goBackBefore(route: BoloRoute): Boolean {
        if (!backStack.contains(route)) return false
        backStack.subList(backStack.lastIndexOf(route), backStack.size).clear()
        return true
    }

    fun goHome() {
        backStack.removeRange(1, backStack.size)
    }
}