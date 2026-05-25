package tv.hsrui.bolo.ui.components.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.user.relation.RelationAction
import tv.hsrui.network.feature.user.relation.fetchRelationWith
import tv.hsrui.network.feature.user.relation.modifyRelation
import tv.hsrui.network.login.storage.LoginStorage

@Composable
fun RelationButton(upName: String, mid: Long, modifier: Modifier = Modifier) {
    val snackbarManager: SnackbarManager = koinInject()
    val loginStorage: LoginStorage = koinInject()
    var trigger by rememberSaveable { mutableStateOf(0) }
    var relationString by rememberSaveable { mutableStateOf("未关注") }
    var isFollowing by rememberSaveable { mutableStateOf(false) }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (loginStorage.isLoggedIn) {
            try {
                val result = fetchRelationWith(mid)
                println(result)
                if (result.isSuccess) {
                    relationString = result.data.to.relationString
                    isFollowing = result.data.to.isFollowing
                } else {
                    snackbarManager.showMessage("[${result.code}]: ${result.message}")
                }

            } catch (e: Exception) {
                snackbarManager.showMessage(e.message ?: "其他网络错误")
            }
        }
    }

    Button(
        onClick = { showDialog = true },
        modifier = modifier.defaultMinSize(minWidth = 56.dp, minHeight = 24.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors()
            .copy(containerColor = if (isFollowing) BiliColor.ThemeColor else Color.Gray)
    ) {
        Text(
            text = relationString,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }

    if (showDialog) {
        val dialogTitleString: String
        val relationAction: RelationAction
        val scope = rememberCoroutineScope()

        if (isFollowing) {
            dialogTitleString = "取消关注 $upName ?"
            relationAction = RelationAction.UnFollow
        } else {
            dialogTitleString = "关注 $upName ?"
            relationAction = RelationAction.Follow
        }

        ShowConfirmDialog(
            title = { Text(dialogTitleString, Modifier.padding(bottom = 16.dp)) },
            onCancel = { showDialog = false },
            onConfirm = {
                scope.launch {
                    try {
                        val result = modifyRelation(mid, relationAction)
                        if (result.isSuccess) {
                            snackbarManager.showMessage("${relationAction.title} $upName 成功")
                        } else {
                            snackbarManager.showMessage("[${result.code}]: ${result.message}")
                        }
                    } catch (e: Exception) {
                        snackbarManager.showMessage(e.message ?: "其他网络错误")
                    } finally {
                        trigger++
                        showDialog = false
                    }
                }
            }
        )
    }
}