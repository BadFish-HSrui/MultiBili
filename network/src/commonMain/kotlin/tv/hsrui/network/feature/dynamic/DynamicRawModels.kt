package tv.hsrui.network.feature.dynamic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DynamicRawResponse(
    val code: Int = -1,
    val message: String = "",
    val data: DynamicRawData = DynamicRawData()
) {
    @Serializable
    data class DynamicRawData(
        @SerialName("has_more") val canLoadMore: Boolean = false,
        val items: List<DynamicRawItem> = emptyList(),
        val offset: String = ""
    )

    @Serializable
    data class DynamicRawItem(
        @SerialName("type") val typeString: String = "DYNAMIC_TYPE_NONE",
        val modules: DynamicRawModules = DynamicRawModules()
    ) {
        val upInfo by modules::upInfo
        val state by modules::state
        val main by modules.main::main

        @Serializable
        data class DynamicRawModules(
            @SerialName("module_author") val upInfo: DynamicRawAuthorModule = DynamicRawAuthorModule(),
            @SerialName("module_dynamic") val main: DynamicRawMainModule = DynamicRawMainModule(),
            @SerialName("module_stat") val state: DynamicRawStateModule = DynamicRawStateModule()
        ) {
            @Serializable
            data class DynamicRawAuthorModule(
                val face: String = "",
                val name: String = "",
                val mid: Long = 0,
                @SerialName("pub_time") val pubDateString: String = ""
            )

            @Serializable
            data class DynamicRawStateModule(
                val comment: Comment = Comment(),
                val like: Like = Like()
            ) {
                @Serializable
                data class Comment(
                    val count: Int = 0
                )

                @Serializable
                data class Like(
                    val count: Int = 0
                )
            }

            @Serializable
            data class DynamicRawMainModule(
                @SerialName("major") val main: Main = Main()
            ) {
                @Serializable
                data class Main(
                    val archive: Archive = Archive()
                ) {
                    @Serializable
                    data class Archive(
                        @SerialName("aid") val avid: Long = 0L,
                        @SerialName("bvid") val bvid: String = "",
                        @SerialName("cover") val coverUrl: String = "",
                        @SerialName("stat") val state: State = State(),
                        @SerialName("duration_text") val durationString: String = "",
                        @SerialName("title") val title: String = ""
                    ) {
                        @Serializable
                        data class State(
                            @SerialName("danmaku") val danmakuCountString: String = "0",
                            @SerialName("play") val viewCountString: String = "0",
                        )
                    }
                }
            }
        }
    }
}