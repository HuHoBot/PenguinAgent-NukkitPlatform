# Changelog

## v1.13.0（2026-09-26）

### Bug 修复

- fix: **Nukkit 平台 QQ 机器人无法启动** —— 影子 jar 曾排除 `logback`，而 QQ SDK 的 `Starter`
  静态初始化块直接引用 `ch.qos.logback.core.Context`（链接期硬依赖），触发
  `NoClassDefFoundError` 导致机器人永远连不上。现改为打包 logback（MOT 不提供该库；
  SLF4J provider 在服务端 classloader 上发现，不会与 MOT 的 log4j-slf4j2-impl 冲突）
- fix: **启动失败被静默吞掉** —— `startClient` 原先只 `catch (Exception)`，
  而 `NoClassDefFoundError` / `NoSuchMethodError` 属于 `Error`，会被 `CompletableFuture`
  悄无声息地丢弃，外部表现为「什么都没发生」。现改捕 `Throwable` 并打印完整堆栈
- fix: **扫码登录死循环** —— 用户手动在 config.yml 填好凭据后，扫码流程仍会无限刷新二维码
  （刷屏 + 长期占用公共线程池 worker）。现在每轮与轮询中都会检查凭据是否已存在并主动退出
- fix: **QQ 群收发消息全部失败（影响 Spigot 与 Nukkit）** —— 出站消息原先都不带 `msg_id`，
  被 QQ 开放平台判定为「主动消息」，未申请该权限的机器人会被 `40034105 主动消息失败, 无权限`
  拒绝，表现为机器人完全不回话、聊天不转发。现新增**被动回复票据**：收到群消息时记录
  `msg_id`，5 分钟窗口内每条出站消息自动挂上它和递增的 `msg_seq`（每条最多 5 次），
  覆盖指令回复、进退服通知、游戏聊天转发、Markdown 卡片等全部出站路径
- fix: **Nukkit 背包 PNG 渲染失败** —— `PlayerSkin` 只接受 64x64 / 64x32，而 Bedrock 玩家
  普遍使用 128x128（HD）皮肤，校验抛异常导致整张图渲染失败并回退成文本。现增加皮肤尺寸
  归一化（128x128 / 256x256 与 64x64 的 UV 布局一致，最近邻缩放即可无损还原）
- fix: **Nukkit 文本背包被 QQ markdown 解析成表格** —— `--- | --- | …` 形式的全空行会被
  渲染成一个突兀的方框，现将文本形态包进代码块保持等宽原样输出

### 新功能

- feat: **Nukkit-MOT 平台适配（Bedrock）** —— `server/Nukkit` 从占位模块补齐为完整适配器，并纳入 Gradle 构建
  - feat: 与 Spigot 对齐的配置体系：WebUI 配置读写、`config-version` 迁移、扫码登录写回凭据、陌生群自动收录
  - feat: 命令输出捕获（log4j2 root logger Appender）+ `/huhobot`、`/at`、`/qqbind`、`/send` 四条游戏内指令
  - feat: 背包 / 末影箱 PNG 渲染 —— 移植 Faithful 贴图管线与玩家模型渲染器，新增 Bedrock→Java 物品贴图映射表（706 条 id/meta + 47 条命名空间别名）
  - feat: 离线背包快照（退服采集 + 定时全量 + NBT 落盘），支持离线玩家查询
  - feat: 服务器插件列表 / 命令帮助 / 日志读取，供 AI Agent 使用
- feat: **Nukkit 接入 PlaceholderAPI** —— 对齐上游 v1.13.0 的 PlaceholderAPI 能力，对接
  [PlaceholderAPI-nukkit](https://github.com/Creeperface01/PlaceholderAPI-nukkit)：配置中的
  `%占位符%` 交给它解析（如 `chat-format.from-game`）。与 Spigot 侧同一套做法，**全程反射、
  不引入编译期依赖**（上游是 `repo.opencollab.dev` 的 SNAPSHOT，不让它绑架本仓库构建）；
  未安装 / 未启用 / 接入失败时文本原样保留，`plugin.yml` 声明为 `softdepend`
- feat: **扩展（addon）体系在 Nukkit 侧补齐** —— 上游那套 `registerAddon` /
  `registerBotCommand` / `OnBotRecvMsg` / `OnBotCommand` 只实现在 Spigot 模块，Nukkit 一直缺失。
  现按 Nukkit 事件体系移植：
  - `registerAddon()` / `registerBotCommand()` / `unregisterBotCommand()`
  - `OnBotRecvMsg`（每条群消息）与 `OnBotCommand`（命中自定义命令）两个可取消事件
  - 事件对象提供 `reply()` / `replyMarkdown()` / `replyImage()`；QQ 回调会切到主线程再派发事件
  - ⚠️ `OnBotRecvMsg` / `OnBotCommand` / `MsgPack` 会在 `onEnable` 里**主动预热**：
    Nukkit 每个插件一个 `PluginClassLoader`，查找顺序是「自己的 jar → 全局**已加载**类注册表」，
    而这几个类是懒加载的；不预热的话 addon 注册监听器时会 `ClassNotFoundException`
- fix: **两个扩展钩子从来没被调用过** —— `MessageProvider.onBotReceivedGroupMessage()` 与
  `onBotCommand()` 在 Spigot 侧有实现、README 里也写着，但**全仓库没有任何调用点**，
  第三方扩展事件实际从未触发。现分别接在群消息入口与自定义命令收口点
  （`CommandSupport.executeCustomCommand` 是 `/执行 <key>` 与 `/<key>` 两条路径的唯一汇聚处）
- feat: `YamlConfig` 新增保留注释的定点写入（`set` / `save` / `flatten`），只改写目标键所在行
- feat: `YamlFileEditor` —— 基于行的 YAML 写入器，WebUI 保存不再抹掉 `config.yml` 的说明注释

### 优化

- opt: `settings.gradle.kts` 引入 foojay 工具链解析器，缺少 JDK 8 / JDK 17 时自动下载
- opt: Nukkit 扫码登录改为**异步执行**，未扫码时不再阻塞服务端启动（Spigot 侧仍为同步）
- opt: Nukkit 文本背包按真实槽位输出（Spigot 侧把 0-26 当物品栏，导致快捷栏重复、27-35 不显示）
- opt: Nukkit 配置迁移先读取旧版本号再补键（Spigot 侧因先补 `config-version` 而跳过老配置的版本化迁移）
- opt: Nukkit 影子 jar 排除服务端已自带的 logback / slf4j / gson / snakeyaml，避免与 MOT 的 log4j2 抢 SLF4J 绑定

### 说明

- Nukkit 侧暂未接入 bStats
- 背包渲染资源（约 11 MB）在构建时从 `server/Spigot/src/main/resources/inventory` 同步，不在仓库中重复存放

---

## v1.4.0（2026-08-29）

### 新功能

- feat: `/背包查看 <玩家名>` 命令 — 查看指定在线玩家背包内容（PNG 图片渲染，仅管理员）
- feat: Faithful 32x 贴图包集成 — 物品图标与方块预览使用 Faithful 32x 纹理
- feat: 玩家皮肤预览 — 背包图片中显示玩家正面皮肤
- feat: 等距3D方块预览 — 有面贴图的方块自动合成等距3D效果
- feat: `/qqbind` 权限改为所有玩家可用（默认 true）

### 变更

- change: `/背包查看` 仅管理员可用（`onlyAdmin = true`）

### Bug 修复

- fix: 命令方块等动画贴图渲染异常（自动裁剪为第一帧）
- fix: 铁砧等无面贴图方块使用基础贴图做2D图标
- fix: 箱子贴图从 overrides 目录正确加载（overrides 优先级提升）
- fix: QQ markdown 转义玩家名中 `_` 字符防止被识别为斜体

---

## v1.3.0（2026-08-27）

### 新功能

- feat: 命令黑名单（`command-blacklist`）— 禁止通过 `/执行` 或 Agent `run_command` 运行指定服务器命令
- feat: `/版本` 命令输出中显示文档链接
- feat: WebUI 新增「绑定」和「命令黑名单」配置分区
- feat: WebUI 新增 `features.enable-auth`（头像认证开关）
- feat: `ConfigUpgrader.upgradeValues()` 支持版本化升级已有配置字段
- feat: `BaseCommand.handleMessage()` 实际执行 `onlyAdmin` 拦截（之前仅用于面板显示）

### 变更

- change: motd `post-img` 和 `use-markdown` 默认值改为 `true`
- change: AI Agent 命令（`/agent`、`/newsession`、`/stop`）标记为仅管理员
- change: 配置文件版本升级到 v6，旧版自动迁移 motd 默认值

### Bug 修复

- fix: 自定义命令执行时不再转发到游戏（如群内执行 /test，游戏内不再显示 [QQ] xxx：/test）
- fix: 文档许可证从 GPL v3 更正为 AGPL v3
- fix: 文档网址从 `huhobot.txssb.cn` 更正为 GitHub Pages
- fix: LICENSE.txt 替换为 AGPL v3 正文

### 文档

- docs: 文档全面改版适配 HuHoBotPenguin 分支
- docs: 新增命令黑名单配置说明
- docs: Agent `run_command` 工具标注受黑名单限制
- docs: motd 默认值说明更新

---

## v1.3.0-beta.2（2026-08-24）

### Bug 修复

- fix: 游戏命令执行结果以纯文本（msg_type=0）发送，不再被 QQ 渲染为 Markdown 格式
- fix: SDK `InterAction.getEnvType()` NPE 修复 — `chat_type` 空值检查防止交互事件崩溃

### 新功能

- feat: QQ→游戏消息支持 `&` 颜色符号转换（如 `&a绿色` → 绿色文字）
- feat: `ConfigProvider.convertAmpersandColors()` — `&0-9`、`&a-f`、`&k-r` 自动转为 `§` 颜色码
- feat: `GroupMessageHandler` 和 `PublicCommands.sendGameMessage` 内联颜色转换

### 文档

- docs: 文档全面改版适配 HuHoBotPenguin 分支（绑定系统、AI Agent、WebUI）
- docs: 新增 `Binding/index.md` 绑定系统文档
- docs: 新增 `Agent/index.md` AI Agent 文档
- docs: 命令列表补全所有 fork 新增命令
- docs: 非 Spigot 适配器标记为上游仅供参考

---

## v1.3.0-beta.1（2026-08-24）

### 上游同步（PenguinClient 功能合入）

- feat: `FaceEmojiParser` — QQ 表情标签 `<faceType=N>` 转可读文本 `[表情:name]`
- feat: `MessageAttachmentParser` — 语音/图片/视频/文件标签转可读文本
- feat: `@Commands` 注解重构 — `command`/`describe`/`onlyAdmin` 字段 + `RegisteredCommand` 数据类
- feat: `BaseCommand.DispatchResult` 枚举（HANDLED / NOT_HANDLED / CUSTOM_COMMAND）
- feat: `CustomCommandRegistry` 运行时注册/注销 + 面板自动同步
- feat: `MenuManager` 改为 HTTP API 动态面板（启动时同步，上限 20 条命令）
- feat: `ConfigProvider` 新增 `isAuthenticationEnabled()`、`commandMenuList()`、motd.md 模板
- feat: `QClient.syncGroupPanels()` 启动时一次性同步面板，运行时注册不再重复同步
- feat: `motd.md` Markdown 模板内置，首次启动自动解压
- feat: 动态 `/帮助` 命令 — 自动列出所有已注册命令（含自定义命令）
- feat: `blockMotd` / `unblockMotd` — 按群屏蔽 MOTD 查询
- feat: `GroupSettingsRepository` 新增 `motdBlocked` 群级设置
- feat: `BaseCommand.allCommands()` 全局命令注册表

### Bug 修复

- fix: 绑定游戏账号后自动添加白名单（QQ 直接绑定 + 游戏验证两条路径均已修复）
- fix: `HuHoBotSpigot.companion` 静态实例支持 `getInstance()` 调用
- fix: 命令面板超出上限时日志提示，仅同步前 20 条命令

### 已知限制

- 本地 QQ Bot SDK 版本不含 `MsgPack`/`OnBotRecvMsg`/`OnBotCommand` 事件，暂不支持第三方插件监听 QQ 消息事件

---

## v1.2.2（2026-08-23）

- fix: 去掉重复的 [图片] 标签
- fix: 清理 QQ 消息中的 Minecraft 颜色代码乱码

---

## v1.2.1-hotfix（2026-08-18）

- fix: 只对消息中实际被 @ 的玩家播放叮音效

---

## v1.2.0（2026-08-17）

### 群服互通 @提及

- feat: `/at` 命令 — 游戏内发送 QQ @消息，支持 Tab 补全昵称
- feat: QQ→游戏 @提及蓝色高亮（`§9@玩家名`）+ 叮音效提醒
- feat: `NicknameManager` 昵称 ↔ openid 双向映射，持久化到 `nicknames.dat`
- feat: `resolveAtMentions` 自动将 `@昵称` 转为 QQ `<@openid>` 格式
- feat: `escapeMarkdown` 转义玩家名中 `_` 等特殊字符

### WebUI

- feat: 暗色简约风格 WebUI，14 个配置分组
- feat: 启动时自动生成 24 位随机密码
- fix: 侧边栏空 bug
- fix: 保存后值丢失 bug

### AI Agent 群管理

- feat: 禁言到期时间服务端计算
- feat: @提及识别与用户名显示
- feat: 审批/拒绝通知显示操作管理员用户名

### Bug 修复

- fix: openid 被当作昵称存储导致 Tab 补全显示 openid
- fix: QQ→游戏转发时 openid 被当作发送者显示名
- fix: NicknameManager.load() 自动跳过昵称是 openid 的脏数据

---

## v1.1.0-alpha.3

- AI Agent 系统初版
- SKILL 按需加载系统
- `/motd` 服务器状态查询
- `/stop` 紧急停止命令
- 指令面板自动同步

---

## v1.1.0-alpha.2

- @提及识别与用户名显示修复

---

## v1.1.0-alpha.1

- 初版 WebUI 图形化配置
- AI Agent 群管理功能（禁言、入群审批、自动审批策略）

---

## v1.0.1

- 初始发布版本
