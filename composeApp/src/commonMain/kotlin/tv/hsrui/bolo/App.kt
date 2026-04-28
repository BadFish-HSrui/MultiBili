package tv.hsrui.bolo

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tv.hsrui.bolo.main.MainScreen
import tv.hsrui.bolo.ui.theme.AppTheme

@Composable
@Preview
fun App() {
    AppTheme {
        MainScreen()
    }
}