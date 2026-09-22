package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.feature.reply.actions.deleteReply

@Composable
fun ShowDeleteReplyDialog(
    replyInfo: ReplyItem,
    replySection: ReplySectionType,
    canDelete: () -> Boolean,
    onCancel: () -> Unit,
    onDeleted: () -> Unit,
) {
    var isSubmitting by remember(replySection, replyInfo.rpid) { mutableStateOf(false) }
    val currentCanDelete by rememberUpdatedState(canDelete)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val scope = rememberCoroutineScope()
    val snackbarManager: SnackbarManager = koinInject()

    ShowConfirmDialog(
        onCancel = onCancel,
        cancelEnabled = !isSubmitting,
        confirmEnabled = !isSubmitting && replySection.oid > 0 && replyInfo.rpid > 0 && canDelete(),
        onConfirm = {
            if (!isSubmitting && replySection.oid > 0 && replyInfo.rpid > 0 && currentCanDelete()) {
                isSubmitting = true
                scope.launch {
                    try {
                        if (!currentCanDelete()) return@launch
                        val result = deleteReply(replySection, replyInfo.rpid)
                        if (result.isSuccess) {
                            currentOnDeleted()
                        } else {
                            snackbarManager.showMessage("[${result.code}]: ${result.message.ifBlank { "删除评论失败" }}")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        snackbarManager.showMessage(e.message?.takeIf { it.isNotBlank() } ?: "删除评论失败")
                    } finally {
                        isSubmitting = false
                    }
                }
            }
        },
    ) {
        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
        Text("删除评论", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "删除评论后，该评论及其下所有回复都会被删除，是否继续？",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp).padding(vertical = 12.dp),
        )
    }
}
