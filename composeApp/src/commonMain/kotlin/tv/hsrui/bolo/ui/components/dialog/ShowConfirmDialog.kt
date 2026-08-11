package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.theme.BoloShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowConfirmDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    cancelEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = {
            if (cancelEnabled) onCancel()
        },
    ) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = BoloShapes.InfoCard.Default,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
            ) {
                content()

                Row {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = cancelEnabled,
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

@Composable
fun ShowConfirmDialog(
    title: @Composable () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    text: String = "",
) {
    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = onConfirm,
    ) {
        if (icon != null) icon()

        title()

        if (text.isNotEmpty()) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }
}
