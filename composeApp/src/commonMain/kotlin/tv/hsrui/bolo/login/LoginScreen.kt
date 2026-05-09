package tv.hsrui.bolo.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.LocalNavigator

@Preview
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (getPlatform().type != PlatformType.Desktop) {
                Button(
                    onClick = { navigator.navigateTo(BoloRoute.Login.Webview) },
                ) {
                    Text("Webview网页登录")
                }
            }
        }
    }
}