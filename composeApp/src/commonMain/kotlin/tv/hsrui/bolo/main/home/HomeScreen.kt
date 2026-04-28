package tv.hsrui.bolo.main.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.main.home.popular.PopularPage

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    PopularPage(modifier = modifier)
}