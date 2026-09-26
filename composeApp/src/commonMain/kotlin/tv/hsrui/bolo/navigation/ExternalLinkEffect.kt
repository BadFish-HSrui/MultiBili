package tv.hsrui.bolo.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.utils.getText
import tv.hsrui.bolo.utils.url.isSystemLinkHandlingEnabled
import tv.hsrui.bolo.utils.url.observeExternalLinkActivation
import tv.hsrui.bolo.utils.url.setSystemLinkHandlingEnabled

@Composable
fun ExternalLinkEffect() {
    val settings: BoloSettings = koinInject()
    val navigator: Navigator = koinInject()
    val snackbar: SnackbarManager = koinInject()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val platform = remember { getPlatform().type }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    val handler = remember(settings, navigator, clipboard, scope) {
        ExternalLinkHandler(
            scope = scope,
            clipboardEnabled = { settings.general.clipboardLinkRecognitionEnabled },
            systemLinksEnabled = { platform == PlatformType.Android && settings.general.systemLinkHandlingEnabled },
            readClipboard = { clipboard.getText() },
            isCurrentRoute = { navigator.backStack.lastOrNull() == it },
            navigate = navigator::navigateTo,
            showMessage = { snackbar.showMessage(it) },
        )
    }

    LaunchedEffect(settings.general.systemLinkHandlingEnabled) {
        if (platform == PlatformType.Android && !setSystemLinkHandlingEnabled(settings.general.systemLinkHandlingEnabled)) {
            settings.general.systemLinkHandlingEnabled = isSystemLinkHandlingEnabled()
            snackbar.showMessage("系统链接设置失败")
        }
    }
    // 显式订阅两个设置，开关关闭时立即取消相应的读取、弹窗和待完成请求。
    val clipboardEnabled = settings.general.clipboardLinkRecognitionEnabled
    val systemLinksEnabled = settings.general.systemLinkHandlingEnabled
    SideEffect {
        if (!clipboardEnabled || !systemLinksEnabled) handler.onSettingsChanged()
    }
    DisposableEffect(handler, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (platform != PlatformType.Desktop) {
                when (event) {
                    Lifecycle.Event.ON_START -> handler.onActivation()
                    Lifecycle.Event.ON_STOP -> handler.onBackground()
                    else -> Unit
                }
            }
        }
        lifecycle.addObserver(observer)
        val stopObserving = observeExternalLinkActivation(handler::onActivation)
        onDispose {
            stopObserving()
            lifecycle.removeObserver(observer)
            handler.close()
        }
    }
    LaunchedEffect(handler, lifecycleState, focused) {
        handler.setInteractive(lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && focused)
    }
    LaunchedEffect(handler) {
        ExternalLinkHandler.systemLinks.filterNotNull().collect(handler::handleSystemLink)
    }

    handler.clipboardLink?.let { link ->
        ShowConfirmDialog(
            onCancel = handler::dismissClipboard,
            onConfirm = handler::openClipboard,
            confirmEnabled = !handler.resolvingClipboard,
            confirmText = "打开",
        ) {
            Text("识别到剪贴板链接", style = MaterialTheme.typography.titleLarge)
            Text(
                text = link,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp).heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}
