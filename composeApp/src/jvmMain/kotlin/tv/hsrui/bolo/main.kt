package tv.hsrui.bolo

import androidx.compose.material.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import tv.hsrui.network.feature.popular.fetchPopularVideos

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MultiBili",
    ) {
//        App()
        Text((runBlocking{ fetchPopularVideos().toString() }))
    }
}