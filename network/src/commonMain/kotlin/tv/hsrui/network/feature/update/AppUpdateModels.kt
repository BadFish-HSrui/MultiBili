package tv.hsrui.network.feature.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppReleaseData(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<AppReleaseAssetData> = emptyList(),
) {
    val universalApk: AppReleaseAssetData?
        get() = assets.singleOrNull {
            it.name == "multi-bili-android-universal-${tagName.removePrefix("v")}.apk"
        }
}

@Serializable
data class AppReleaseAssetData(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)
