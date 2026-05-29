package tv.hsrui.bolo.boloSetting.setting.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import multibili.composeapp.generated.resources.AppIconSquare
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.app_name
import multibili.composeapp.generated.resources.github_repo_url
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import tv.hsrui.bolo.BuildInfo
import tv.hsrui.bolo.utils.url.openUrl

@Preview(showBackground = true)
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val githubRepoUrlString = stringResource(Res.string.github_repo_url)
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = (-32).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Res.drawable.AppIconSquare),
                contentDescription = null,
                modifier = Modifier
                    .sizeIn(maxWidth = 192.dp)
                    .aspectRatio(1F)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(percent = 20))
            )
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text("tv.hsrui.bolo / ${BuildInfo.appVersion}")

            Surface(
                onClick = { scope.launch { openUrl(githubRepoUrlString) } },
                color = Color.Transparent
            ) { Text(text = githubRepoUrlString) }

            Text(
                text = "本项目与哔哩哔哩官方无关\n请勿在国内社交平台公开传播",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.5F)
            )
        }


    }
}