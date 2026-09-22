package tv.hsrui.bolo.navigation

import tv.hsrui.bolo.player.session.BoloPlaybackSession
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class Navigator {
    val backStack: SnapshotStateList<BoloRoute> = mutableStateListOf(BoloRoute.Main)
    val currentDepth: Int get() = (backStack.size - 1)
    fun navigateTo(route: BoloRoute) {
        BoloPlaybackSession.current?.close()
        backStack.add(route)
    }

    fun goBack() {
        if (backStack.size > 1) {
            BoloPlaybackSession.current?.close()
            backStack.removeLast()
        }
    }

    fun goBackBefore(route: BoloRoute): Boolean {
        if (!backStack.contains(route)) return false
        BoloPlaybackSession.current?.close()
        backStack.subList(backStack.lastIndexOf(route), backStack.size).clear()
        return true
    }

    fun goHome() {
        BoloPlaybackSession.current?.close()
        backStack.removeRange(1, backStack.size)
    }
}