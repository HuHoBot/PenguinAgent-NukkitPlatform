package cn.huohuas001.huhobotPenguin.nukkit.onlinelist

/**
 * 在线列表渲染用玩家快照（主线程快照，不可变）。
 *
 * 与 Spigot 侧的区别：Bedrock 客户端的皮肤直接随登录包上传，服务端手上就有原始像素，
 * 不需要再去外部皮肤接口按 URL 下载，因此这里存的是 RGBA 字节而不是贴图地址。
 */
class OnlineListPlayer(
    val name: String,
    val uuid: String,
    /** 皮肤原始 RGBA 数据（Bedrock 常见 128×128 或 64×64）；为空时渲染占位头像。 */
    val skinData: ByteArray?,
    val skinWidth: Int,
    val skinHeight: Int,
    val administrator: Boolean
)

/** 在线列表渲染用服务器快照。 */
class ServerSnapshot(
    val serverName: String,
    val maxPlayers: Int,
    val totalOnlinePlayers: Int,
    val players: List<OnlineListPlayer>,
    val capturedAt: Long = System.currentTimeMillis(),
    val currentPage: Int = 1,
    val totalPages: Int = 1
)
