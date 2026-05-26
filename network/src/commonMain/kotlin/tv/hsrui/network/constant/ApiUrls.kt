package tv.hsrui.network.constant

object ApiUrls {
    const val BASE = "https://api.bilibili.com/"  //B站API地址
    const val POPULAR = "x/web-interface/popular"  //热门视频
    const val RECOMMEND = "x/web-interface/wbi/index/top/feed/rcmd" //首页推荐
    const val REGION = "x/web-interface/region/feed/rcmd" //分区
    const val ACCOUNT_INFO = "x/space/v2/info" //个人空间信息
    const val MY_ACCOUNT_INFO = "x/space/v2/myinfo" //我的个人空间信息
    const val FOLLOW_STATE = "x/relation/stat" //关注与被关注
    const val HISTORY = "x/web-interface/history/cursor" //历史记录
    const val HISTORY_DELETE = "x/v2/history/delete" //删除历史记录
    const val DYNAMIC = "x/polymer/web-dynamic/v1/feed/all" //动态
    const val VIDEO_INFO = "x/web-interface/wbi/view" //视频播放信息
    const val VIDEO_RELATED = "x/web-interface/archive/related" //相关视频推荐
    const val RELATION = "x/web-interface/relation" //关系
    const val MODIFY_RELATION = "x/relation/modify" //修改关系

    object VideoAction {
        const val LIKE = "x/web-interface/archive/like" //点赞
        const val HAS_LIKE = "x/web-interface/archive/has/like" //点赞状态
        const val COIN = "x/web-interface/coin/add" // 投币
        const val HAS_COIN = "x/web-interface/archive/coins" //投币状态
        const val HAS_FAVOURED = "x/v2/fav/video/favoured" //收藏状态
        const val TRIPLE = "x/web-interface/archive/like/triple" //一键三连
    }

    object WatchLater {
        const val LIST = "x/v2/history/toview" //稍后再看列表
        const val ADD = "x/v2/history/toview/add" //添加稍后再看
        const val DELETE = "x/v2/history/toview/del" //移除稍后再看
    }
}