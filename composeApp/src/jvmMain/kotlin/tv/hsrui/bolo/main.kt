package tv.hsrui.bolo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.jvm_icon
import org.jetbrains.compose.resources.painterResource
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.download.DownloadFiles
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow
import tv.hsrui.bolo.player.DesktopPlayerKeyboard
import tv.hsrui.bolo.player.session.BoloPlaybackSession
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import java.awt.Dimension

fun main() {
    System.setProperty("compose.interop.blending", "true")
    FileKit.init(appId = "tv.hsrui.bolo")
    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        Window(
            onCloseRequest = {
                if (!closing) {
                    closing = true
                    scope.launch {
                        try {
                            withTimeout(10_000) {
                                BoloPlaybackSession.current?.player?.controller?.pauseAndObserve()
                                BoloPlaybackSession.current?.close()
                                getKoin().get<AppDataStorage>().playbackStatistics.flush()
                            }
                            exitApplication()
                        } catch (_: TimeoutCancellationException) {
                            showSnackbarMessage("播放记录保存超时，请稍后重试关闭")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            showSnackbarMessage("播放记录保存失败，请稍后重试关闭")
                        } finally { closing = false }
                    }
                }
            },
            state = windowState,
            onPreviewKeyEvent = DesktopPlayerKeyboard::onPreviewKeyEvent,
            onKeyEvent = { event ->
                DesktopPlayerFullscreenWindow.onKeyEvent(event) || DesktopPlayerKeyboard.onKeyEvent(event)
            },
            title = "Multi Bili",
            icon = painterResource(Res.drawable.jvm_icon)
        ) {
            val inputMethod = remember(window) { DesktopInputMethod(window) }
            val inputInterceptor = remember(inputMethod) {
                PlatformTextInputInterceptor { request, nextHandler ->
                    inputMethod.scheduleUpdate()
                    try {
                        nextHandler.startInputMethod(request)
                    } finally {
                        inputMethod.scheduleUpdate()
                    }
                }
            }
            DisposableEffect(window, windowState) {
                window.minimumSize = Dimension(720, 600)
                DesktopPlayerFullscreenWindow.attach(window, windowState)
                inputMethod.attach()
                DownloadFiles.attach(window)
                onDispose {
                    inputMethod.close()
                    DownloadFiles.detach(window)
                    DesktopPlayerFullscreenWindow.detach(window)
                }
            }
            InterceptPlatformTextInput(inputInterceptor) {
                Box(Modifier.onPreviewKeyEvent(DesktopPlayerKeyboard::onContentPreviewKeyEvent)) {
                    App()
                }
            }
        }
    }
}
