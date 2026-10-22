package tv.hsrui.network.utils

import java.security.MessageDigest

fun String.toMD5(): String =
    MessageDigest.getInstance("MD5").digest(encodeToByteArray()).toHexString()
