package tv.hsrui.bolo.player.base

import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64

internal expect fun loadBoloMpvLibrary()

internal object BoloMpvNative {
    init { loadBoloMpvLibrary() }
    external fun create(platform: String): Long
    external fun load(handle: Long, video: String, audio: String?, start: Double, generation: Long, userAgent: String, referrer: String): Int
    external fun pause(handle: Long, paused: Boolean): Int
    external fun speed(handle: Long, speed: Double): Int
    external fun volume(handle: Long, volume: Double): Int
    external fun mergeAudioChannels(handle: Long, enabled: Boolean): Int
    external fun seek(handle: Long, seconds: Double, request: Long): Int
    external fun poll(handle: Long): DoubleArray?
    external fun info(handle: Long): ByteArray?
    external fun stop(handle: Long): Int
    external fun caFile(handle: Long, path: String): Int
    external fun surface(handle: Long, surface: Any?): Int
    external fun surfaceSize(handle: Long, width: Int, height: Int): Int
    external fun renderCreate(handle: Long): Int
    external fun renderDirty(handle: Long): Boolean
    external fun render(handle: Long, fbo: Int, width: Int, height: Int, flip: Boolean): Int
    external fun renderFree(handle: Long)
    external fun destroy(handle: Long)
}

internal fun DoubleArray.toMpvEvent() = BoloMpvEvent(this[0].toInt(), this[1].toLong(), this[2].toLong(), this[3].toInt(), this[4])

/** mbedTLS 使用宿主信任管理器导出的 CA；保留证书验证，不绑定过时的内置 CA 包。 */
internal fun configureBoloMpvCertificates(handle: Long, directory: File) {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    val certificates = factory.trustManagers.filterIsInstance<X509TrustManager>().flatMap { it.acceptedIssuers.toList() }
    check(certificates.isNotEmpty()) { "系统未提供 HTTPS 信任根证书" }
    val pem = certificates.joinToString("") { certificate ->
        "-----BEGIN CERTIFICATE-----\n" + Base64.encode(certificate.encoded).chunked(64).joinToString("\n") + "\n-----END CERTIFICATE-----\n"
    }
    val hash = MessageDigest.getInstance("SHA-256").digest(pem.toByteArray()).joinToString("") { "%02x".format(it) }
    directory.mkdirs()
    val file = File(directory, "$hash.pem")
    if (!file.exists() || file.readText() != pem) {
        val temporary = File.createTempFile("certificates-", ".pem", directory)
        try {
            temporary.writeText(pem)
            check(temporary.renameTo(file) || (file.exists() && file.readText() == pem))
        } finally { temporary.delete() }
    }
    check(BoloMpvNative.caFile(handle, file.absolutePath) >= 0) { "HTTPS 信任根配置失败" }
}
