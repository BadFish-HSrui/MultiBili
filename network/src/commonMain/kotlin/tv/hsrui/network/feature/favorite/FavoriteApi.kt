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

suspend fun fetchMyFavoriteFolders(targetAvid: Long? = null): FavoriteFolderListResponse {
    val loginStorage: LoginStorage = getKoin().get()
    if (!loginStorage.isLoggedIn) {
        return FavoriteFolderListResponse(message = "账号未登录")
    }

    return fetchFavoriteFolders(loginStorage.cookies.dedeUserID, targetAvid)
}

suspend fun fetchFavoriteFolders(mid: Long, targetAvid: Long? = null): FavoriteFolderListResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Favorite.CREATED_FOLDERS) {
        parameter("up_mid", mid)
        if (targetAvid != null) {
            parameter("type", 2)
            parameter("rid", targetAvid)
        }
    }
    val folderListResponse: FavoriteFolderListResponse = response.body()
    if (!folderListResponse.isSuccess || folderListResponse.isHidden || targetAvid != null) {
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

suspend fun createFavoriteFolder(title: String, isPrivate: Boolean): FavoriteFolderInfoResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Favorite.CREATE_FOLDER) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("title", title.trim())
                    append("privacy", if (isPrivate) "1" else "0")
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}

suspend fun deleteFavoriteFolder(mediaId: Long): ModifyFavoriteResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Favorite.DELETE_FOLDER) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("media_ids", mediaId.toString())
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}

suspend fun fetchFavoriteFolderContent(
    mediaId: Long,
    pageNumber: Int = 1,
    pageSize: Int = 20,
    keyword: String = "",
): FavoriteFolderContentResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.Favorite.FOLDER_CONTENT) {
        parameter("media_id", mediaId)
        parameter("pn", pageNumber)
        parameter("ps", pageSize)
        if (keyword.isNotBlank()) parameter("keyword", keyword)
        parameter("order", "mtime")
        parameter("type", 0)
        parameter("platform", "web")
    }

    return response.body()
}

suspend fun removeFavoriteVideo(mediaId: Long, video: FavoriteVideoCard): ModifyFavoriteResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Favorite.REMOVE_RESOURCE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("resources", video.resourceKey)
                    append("media_id", mediaId.toString())
                    append("platform", "web")
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}

suspend fun modifyVideoFavoriteFolders(
    avid: Long,
    addMediaIds: Collection<Long>,
    removeMediaIds: Collection<Long>,
): ModifyFavoriteResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Favorite.MODIFY_RESOURCE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("rid", avid.toString())
                    append("type", "2")
                    if (addMediaIds.isNotEmpty()) {
                        append("add_media_ids", addMediaIds.joinToString(","))
                    }
                    if (removeMediaIds.isNotEmpty()) {
                        append("del_media_ids", removeMediaIds.joinToString(","))
                    }
                    append("platform", "web")
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}
