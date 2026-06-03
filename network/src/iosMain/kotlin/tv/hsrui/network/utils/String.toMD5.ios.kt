package tv.hsrui.network.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.get
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun String.toMD5(): String = memScoped {
    val digest = allocArray<kotlinx.cinterop.UByteVar>(CC_MD5_DIGEST_LENGTH)
    val inputBytes = this@toMD5.encodeToByteArray()
    
    inputBytes.usePinned { pinned ->
        CC_MD5(pinned.addressOf(0), inputBytes.size.toUInt(), digest)
    }
    
    (0 until CC_MD5_DIGEST_LENGTH).joinToString("") {
        digest[it].toString(16).padStart(2, '0')
    }
}