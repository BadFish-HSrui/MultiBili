package tv.hsrui.bolo.ui.components.dropdownMenu.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.watchLater.addWatchLater

@Composable
fun WatchLaterMenuItem(avid: Long, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarManager: SnackbarManager = koinInject()

    DropdownMenuItem(
        text = { Text("稍后再看") },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.WatchLater,
                contentDescription = null
            )
        },
        onClick = {
            scope.launch {
                try {
                    val result = addWatchLater(avid)
                    if (result.isSuccess) {
                        snackbarManager.showMessage("添加成功")
                    } else {
                        snackbarManager.showMessage("[${result.code}]: ${result.message}")
                    }
                } catch (e: Exception) {
                    snackbarManager.showMessage(e.message ?: "其他网络错误")
                } finally {
                    onDismiss()
                }
            }
        }
    )
}