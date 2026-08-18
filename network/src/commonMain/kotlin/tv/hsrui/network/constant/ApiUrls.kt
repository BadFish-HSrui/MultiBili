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
        const val DELETE_ALL = "x/v2/history/toview/clear" //清空稍后再看
    }

    object Favorite {
        const val CREATED_FOLDERS = "x/v3/fav/folder/created/list-all" //创建的收藏夹列表
        const val CREATE_FOLDER = "x/v3/fav/folder/add" //创建收藏夹
        const val DELETE_FOLDER = "x/v3/fav/folder/del" //删除收藏夹
        const val FOLDER_INFO = "x/v3/fav/folder/info" //收藏夹元数据
        const val FOLDER_CONTENT = "x/v3/fav/resource/list" //收藏夹内容
        const val MODIFY_RESOURCE = "x/v3/fav/resource/deal" //修改视频收藏夹归属
        const val REMOVE_RESOURCE = "x/v3/fav/resource/batch-del" //移除收藏夹内容
    }

    object Reply {
        const val LIST = "x/v2/reply/wbi/main" //评论列表
        const val SUB_LIST = "x/v2/reply/reply" //子评论列表
        const val LIKE = "x/v2/reply/action" //点赞
        const val DISLIKE = "x/v2/reply/hate" //点踩
        const val SEND = "x/v2/reply/add" //发送评论
    }

    object Play {
        const val VIDEO = "x/player/wbi/playurl" //视频播放信息
    }

    const val WBI = "x/web-interface/nav" //用于获取WbiKey，实际上是导航栏接口
}
