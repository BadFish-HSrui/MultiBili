package tv.hsrui.network.feature.favorite

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun fetchCreatedFavoriteFolders(): FavoriteFolderListResponse {
    val loginStorage: LoginStorage = getKoin().get()
    if (!loginStorage.isLoggedIn) {
        return FavoriteFolderListResponse(message = "账号未登录")
    }

    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Favorite.CREATED_FOLDERS) {
        parameter("up_mid", loginStorage.cookies.dedeUserID)
    }
    val folderListResponse: FavoriteFolderListResponse = response.body()
    if (!folderListResponse.isSuccess) {
        return folderListResponse
    }

    return coroutineScope {
        val requestSemaphore = Semaphore(permits = 4)
        val folders = folderListResponse.folders.map { folder ->
            async {
                requestSemaphore.withPermit {
                    repeat(3) { attempt ->
                        try {
                            val folderInfoResponse = fetchFavoriteFolderInfo(folder.id)
                            val folderInfo = folderInfoResponse.folder
                            if (folderInfoResponse.isSuccess && folderInfo != null) {
                                return@withPermit folderInfo
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }

                        if (attempt < 2) {
                            delay(100)
                        }
                    }

                    folder
                }
            }
        }.awaitAll()

        folderListResponse.withFolders(folders)
    }
}

suspend fun fetchFavoriteFolderInfo(mediaId: Long): FavoriteFolderInfoResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Favorite.FOLDER_INFO) {
        parameter("media_id", mediaId)
    }

    return response.body()
}

suspend fun fetchFavoriteFolderContent(
    mediaId: Long,
    pageNumber: Int = 1,
    pageSize: Int = 20,
): FavoriteFolderContentResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Favorite.FOLDER_CONTENT) {
        parameter("media_id", mediaId)
        parameter("pn", pageNumber)
        parameter("ps", pageSize)
        parameter("order", "mtime")
        parameter("type", 0)
        parameter("platform", "web")
    }

    return response.body()
}

suspend fun removeFavoriteVideo(mediaId: Long, avid: Long): ModifyFavoriteResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Favorite.REMOVE_RESOURCE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("resources", "$avid:2")
                    append("media_id", mediaId.toString())
                    append("platform", "web")
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}
