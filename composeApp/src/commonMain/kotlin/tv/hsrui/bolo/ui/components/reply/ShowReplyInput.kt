package tv.hsrui.bolo.ui.components.reply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ShowReplyInput(
    text: MutableState<String>,
    labelText: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 1000,
    sendContentDescription: String = "发送评论",
    sendEnabled: Boolean = true,
    errorText: String? = null,
) {
    var text by text
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        )
    ) {
        Box(
            Modifier.fillMaxSize()
                .imePadding()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 4.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    isError = errorText != null,
                    supportingText = {
                        Text(
                            text = errorText?.let { "$it · ${text.length}/$maxLength" } ?: "${text.length}/$maxLength",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (sendEnabled && text.length <= maxLength) {
                                    onSend(text)
                                }
                            },
                            enabled = sendEnabled,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = sendContentDescription
                            )
                        }
                    },
                    label = { Text(labelText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}
