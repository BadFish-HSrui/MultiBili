package tv.hsrui.network.login

import kotlinx.serialization.Serializable

@Serializable
internal data class LoginApiResponse<T>(
    private val code: Int = -1,
    private val message: String = "",
    private val data: T? = null,
) {
    fun requireData(): T {
        check(code == 0) { message.takeUnless { it.isBlank() || it == "0" } ?: "登录请求失败（code=$code）" }
        return checkNotNull(data) { "登录接口未返回有效数据" }
    }
}
