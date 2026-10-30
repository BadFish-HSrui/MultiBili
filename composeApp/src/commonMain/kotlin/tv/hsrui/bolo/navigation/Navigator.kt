package tv.hsrui.bolo.navigation

import tv.hsrui.bolo.player.session.BoloSystemMediaOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

class Navigator {
    val backStack: SnapshotStateList<BoloRoute> = mutableStateListOf(BoloRoute.Main)
    val currentDepth: Int get() = (backStack.size - 1)
    internal var skipForwardTransition by mutableStateOf(false)
        private set

    fun navigateTo(route: BoloRoute) {
        BoloSystemMediaOwner.current?.close()
        skipForwardTransition = false
        backStack.add(route)
    }

    fun switchTo(route: BoloRoute) {
        if (backStack.lastOrNull() == route) return
        BoloSystemMediaOwner.current?.close()
        skipForwardTransition = backStack.size > 1
        if (skipForwardTransition) {
            backStack[backStack.lastIndex] = route
        } else {
            backStack.add(route)
        }
    }

    fun goBack() {
        if (backStack.size > 1) {
            BoloSystemMediaOwner.current?.close()
            skipForwardTransition = false
            backStack.removeLast()
        }
    }

    fun goBackBefore(route: BoloRoute): Boolean {
        if (!backStack.contains(route)) return false
        BoloSystemMediaOwner.current?.close()
        skipForwardTransition = false
        backStack.subList(backStack.lastIndexOf(route), backStack.size).clear()
        return true
    }

    fun goHome() {
        BoloSystemMediaOwner.current?.close()
        skipForwardTransition = false
        backStack.removeRange(1, backStack.size)
    }
}