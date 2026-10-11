@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package tv.hsrui.bolo.utils.file

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceCreationOptions
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.UniformTypeIdentifiers.UTType
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class ImageSaver actual constructor() {
    actual suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main.immediate) {
        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
        if (status != PHAuthorizationStatusNotDetermined) return@withContext status == PHAuthorizationStatusAuthorized
        suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { result ->
                if (continuation.isActive) continuation.resume(result == PHAuthorizationStatusAuthorized)
            }
        }
    }

    actual suspend fun saveImage(image: ImageSaveData): Boolean = withContext(Dispatchers.IO) {
        require(image.bytes.isNotEmpty()) { "图片数据为空" }
        val type = checkNotNull(UTType.typeWithMIMEType(image.mimeType)) { "系统不支持此图片格式" }
        val data = image.bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = image.bytes.size.toULong()) }
        currentCoroutineContext().ensureActive()
        suspendCancellableCoroutine { continuation ->
            if (!continuation.isActive) return@suspendCancellableCoroutine
            // 已提交的 Photos 事务不能取消；关闭查看器只取消等待，不尝试删除已保存图片。
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    val options = PHAssetResourceCreationOptions().apply {
                        uniformTypeIdentifier = type.identifier
                        // 不提供 originalFilename，由 Photos 为原始数据生成名称。
                    }
                    PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(
                        PHAssetResourceTypePhoto, data, options,
                    )
                },
                completionHandler = { success, error ->
                    if (continuation.isActive) {
                        if (success) continuation.resume(true)
                        else continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "系统照片库未能保存图片"))
                    }
                },
            )
        }
    }
}
