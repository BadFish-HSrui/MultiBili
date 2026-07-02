package tv.hsrui.bolo.ui.components.topBar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowTopBarWithNavigationButton(
    modifier: Modifier = Modifier,
    goBackBefore: BoloRoute? = null,
    title: @Composable () -> Unit = { Text(stringResource(Res.string.app_name)) }
) {
    val navigator: Navigator = koinInject()

    CenterAlignedTopAppBar(
        title = title,
        navigationIcon = {
            Row {
                IconButton(
                    onClick = {
                        if (goBackBefore == null) navigator.goBack()
                        else navigator.goBackBefore(goBackBefore)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                        contentDescription = "返回"
                    )
                }
                if (navigator.currentDepth > 1) {
                    IconButton(
                        onClick = { navigator.goHome() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "回到主页"
                        )
                    }
                }
            }
        },
        modifier = modifier
    )
}