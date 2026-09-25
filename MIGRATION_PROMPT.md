# 平台迁移提示词：把 Spigot 适配器迁移到 Nukkit-MOT

> 这是从一次真实的 Spigot → Nukkit-MOT 迁移（HuHoBotPenguin，约 4600 行 Spigot 专属代码）
> 中逐条提炼出的操作提示词。直接把它交给 AI 作为任务描述即可。
> 带 ⚠️ 的是**真实踩过并付出代价**的坑，务必逐条核对。

---

## 你的任务

把本仓库的 Minecraft 服务端适配器从 **Spigot/Paper** 迁移到 **Nukkit-MOT（Bedrock）**。
目标是**功能对齐**：Nukkit 侧应具备与 Spigot 侧相同的配置键、指令、AI Agent 行为，
以及（如果适用）图像渲染能力。

**不要重写共享层**。这类项目通常已经分好了层，迁移是「实现平台差异」，不是「重写项目」。

---

## 阶段 0：先侦察，不要先写代码

凭记忆写平台 API 一定会错。按顺序做完这几件事再动手：

1. **确认目标平台的具体分支**。Nukkit 生态有多个不兼容分支：
   - Cloudburst Nukkit（`cn.nukkit:nukkit`，OpenCollab 仓库）
   - **Nukkit-MOT**（`cn.nukkit:Nukkit:MOT-SNAPSHOT`，`repo.lanink.cn`）
   - PowerNukkitX
   三者 API 不同。例如 `CommandSender.sendCommandOutput(CommandOutputContainer)`
   只有 MOT/PowerNukkitX 有，Cloudburst 没有。
   **判据**：去看仓库里已有的平台桩代码（stub）用了哪些类，那才是目标分支。

2. **同时下载 jar 和 sources jar**，把源码解压出来当参考：
   ```bash
   curl -sSL -o nukkit.jar "<repo>/.../Nukkit-MOT-<ts>-<build>.jar"
   curl -sSL -o nukkit-sources.jar "<repo>/.../Nukkit-MOT-<ts>-<build>-sources.jar"
   unzip -q nukkit-sources.jar -d nukkit-src
   ```
   源码比 javap 有用得多——能直接看到实现细节和注释。

3. **用 `javap` 逐个核对要调用的 API**（签名、返回类型、泛型）：
   ```bash
   javap -cp nukkit.jar cn.nukkit.Server | grep -E "getOnlinePlayers|getCommandMap"
   ```
   ⚠️ **`javap` 默认只显示类自己声明的成员，不显示继承来的**。
   例如 `Player.getInventory()` 实际声明在 `EntityHumanType` 上，
   在 `Player` 的 javap 输出里根本看不到。找不到方法时去 grep 源码的类层次。

4. **确认仓库可达性**。部分国内镜像（如 `repo.lanink.cn`）会间歇性超时，
   探测时要重试；GitHub Releases 下载 JDK 在国内常被墙，准备国内镜像兜底。

---

## 阶段 1：把构建跑通（先不写业务代码）

顺序很重要——先让空模块编译通过，再往里填。

1. `settings.gradle.kts` 里取消目标平台的 `include`。
2. 写平台模块的 `build.gradle.kts`：
   - `compileOnly` 服务端 API（**绝不打进产物**）
   - 平台侧需要的第三方库用 `implementation`
   - 若共享模块用 `implementation` 引入了某库而平台代码要直接用，**平台模块必须自己再声明一次**
     （`implementation` 不会传递给消费者，但会出现在 `runtimeClasspath`，所以影子 jar 里是有的）
3. ⚠️ **工具链**：如果共享模块要求 JDK 8、平台模块要求 JDK 17，而机器上只有一个 JDK，
   构建会以 `No matching toolchains found` 失败。在 `settings.gradle.kts` 加：
   ```kotlin
   plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }
   ```
   ⚠️ foojay 从 GitHub Releases 下载 JDK，国内常失败。可手动装一个再从**用户级**
   `~/.gradle/gradle.properties` 指定（不要写进仓库）：
   ```
   org.gradle.java.installations.paths=/path/to/jdk8
   ```
4. **跑一次 `compileKotlin` 验证构建链路**，确认能解析到服务端 API。

---

## 阶段 2：平台核心实现

按「共享层接口」逐个实现，通常长这样：

```kotlin
class YourPlugin : PluginBase(), SharedPlatformInterface {
    override fun getPlatform(): String = "Nukkit"
    override fun createCommandExecutor(): HExecution = ...
    override fun broadcastMessage(msg: String) { ... }
    override fun submit(task: Runnable): Cancelable = ...
    // ... 其余全部 override
}
```

**关键点：先把接口的默认实现读一遍**。共享层通常已给了大量默认值，
你只需要 override 平台真正不同的那几个（配置读取、原生命令执行、广播、调度）。

### 配置层

- 若已有跨平台 YAML 读取器（如基于 snakeyaml 的），直接复用，补齐缺失的访问器。
- ⚠️ **配置写入要保留注释**。snakeyaml / Bukkit 的 `saveConfig()` 整体重新序列化会
  **抹掉用户文件里的全部注释**——对一份满是说明的 `config.yml` 是灾难。
  实现一个**基于行的定点写入器**：只改写目标键所在的行/块。
  要处理：嵌套路径、标量/列表/对象列表、键不存在时按缩进追加、原子写回。
- ⚠️ **配置版本迁移要「先读旧版本号再补键」**。把 `config-version` 放进「默认值补齐表」
  并先执行补齐，会让没有版本号的老配置被当成最新版，**版本化迁移被静默跳过**。

### 原生命令执行

- ⚠️ **派发命令要用服务端真实控制台 sender**，不要自定义 `CommandSender`：
  原版命令的参数解析常常做强转，自定义 sender 会抛异常。
- 输出捕获：
  - Spigot 用 log4j2 root logger Appender
  - ⚠️ **Nukkit-MOT 的 `MainLogger` 本身就是 Lombok `@Log4j2`**，
    所以**同一套 Appender 方案可以原样复用**，不需要另造轮子。
  - 派发后固定延迟（如 40 tick = 2 秒）再收集输出。

### 指令注册

- ⚠️ **Nukkit 会自动注册 `plugin.yml` 里声明的指令**（`PluginManager.parseYamlCommands`），
  执行的默认是插件自己的 `onCommand`。
  **不要再手动 `server.commandMap.register(...)`**，否则会重复注册。
  正确做法：全部在 `plugin.yml` 里声明，在 `onCommand` 里按 `command.name` 分发。

### 调度

- Nukkit 调度延迟/周期单位是 **tick**（20 tick = 1 秒），与 Spigot 一致。
- ⚠️ **默认的 `submitAsync` 走 `CompletableFuture.runAsync`（公共 ForkJoinPool）**。
  在公共池里做 `Thread.sleep` 长循环会长期占用 worker；写无限循环的逻辑要能主动退出。

### 服务端信息（供 AI Agent 用）

- 插件列表：`server.pluginManager.plugins.values.map { it.name }`
- 命令表：`server.commandMap.commands`（`SimpleCommandMap` 直接返回 `Map<String, Command>`）
- 命令详情：`Command.getName/getAliases/getDescription/getUsage/getPermission`
  + `(cmd as? PluginIdentifiableCommand)?.plugin?.name`
- ⚠️ 日志文件名不同：Spigot 是 `logs/latest.log`，**Nukkit-MOT 是 `logs/server.log`**。
  读日志前先探测多个候选路径。

---

## 阶段 3：物品/方块与图像渲染（如有）

这是工作量最大、坑最密的部分。

### 物品标识

- Spigot 用扁平化名称（`Material.key` → `diamond_sword`）
- ⚠️ **Bedrock 用「数字 id + meta」或命名空间 id**，需要一张映射表。
- ⚠️ **方块 id > 255 在物品空间是负数**：`Block.getItemId()` / `Item.getBlockItem()`
  会映射成 `255 - blockId`，而 `Item.getId()` 返回的就是这个负值。
  **查表时必须用 `item.id` 原值，绝不能用 `item.block.id`。**
- 解析顺序建议：
  1. `(id, meta)` 精确表
  2. 命名空间 id 别名表
  3. 命名空间 id 去掉 `minecraft:` 前缀
  4. `RuntimeItems.getLegacyStringFromLegacyId(id)` 兜底
- **每种 meta 变体家族**都要覆盖：羊毛/地毯/混凝土/染色玻璃/陶瓦/木板/原木/树叶/台阶/楼梯/
  潜影盒/床/旗帜/染料/刷怪蛋/珊瑚…… 建议写脚本**逐一校验映射到的贴图文件真实存在**，
  做到「零未解析项」再交付。

### 玩家皮肤

- ⚠️ **Bedrock 皮肤可能是 128×128 甚至 256×256**，而渲染器的模型加载器往往
  硬校验「必须是 64×64 或 64×32」并直接抛异常 → **整张图渲染失败**。
  必须做**尺寸归一化**：HD 皮肤与标清的 UV 布局一致、只是分辨率翻倍，
  用最近邻缩放到 64×64 即可无损还原。
- 皮肤数据的字节序：Bedrock 是 **RGBA**，`BufferedImage.setRGB` 需要 **ARGB**，要转换。

### 物品堆叠 / 空槽

- ⚠️ Nukkit 判空用 **`Item.isNull()`**，**不是 `isAir()`**（后者不存在）。
- 反序列化可直接用 `Item.setCompoundTag(byte[])` 吃 NBT 字节，省一次手动解析。

### 复用美术资源

- ⚠️ 贴图包动辄 10MB+、上千文件。**不要在仓库里复制一份**。
  用 Gradle `Sync` 任务从已有模块的资源目录同步到 build 目录，再挂成 resource srcDir：
  ```kotlin
  val prepareAssets by tasks.registering(Sync::class) {
      from(rootProject.file("server/Spigot/src/main/resources/inventory")) { into("inventory") }
      into(layout.buildDirectory.dir("generated/inventory-assets"))
  }
  sourceSets.named("main") { resources.srcDir(layout.buildDirectory.dir("generated/inventory-assets")) }
  tasks.processResources { dependsOn(prepareAssets) }
  ```

---

## 阶段 4：打包与依赖边界 ⚠️ 最容易出人命的部分

影子 jar 该排除什么，**不能只看「服务端有没有」**，还要看**链接期依赖**。

### 判断规则

| 情况 | 处理 |
|---|---|
| 服务端自带（Nukkit 有 gson/snakeyaml/slf4j/netty/guava） | 可排除（classloader 父优先，打包也永远不会被加载） |
| 服务端**没有**（okhttp/jsoup/fastjson/logback/zxing） | **必须打包** |
| 服务端自带但我们**只编译期需要**（服务端 API、log4j2 core） | `compileOnly`，不打包 |

### ⚠️ 致命坑：静态初始化块的硬引用

某 SDK 的类在 `<clinit>` 里直接引用了 `ch.qos.logback.core.Context`。
我按「服务端用 log4j2、logback 用不上」把它排除了，结果：
```
java.lang.NoClassDefFoundError: ch/qos/logback/core/Context
    at com.example.Sdk.Starter.<clinit>(Starter.java:75)
```
**只要一个类在静态初始化块里引用到它，它就是硬依赖，跟「日志后端用谁」无关。**

**排查方法**：拿到 `NoClassDefFoundError` 后，直接看 `Caused by: ClassNotFoundException`
指向哪个包，把它加回去。

### ⚠️ 更深一层的坑：失败被静默吞掉

```kotlin
try { client.start() } catch (error: Exception) { log(error.message) }   // ❌
```
`NoClassDefFoundError` / `NoSuchMethodError` 是 **`Error` 不是 `Exception`**，
上面的 catch 抓不到。而如果这段代码在 `CompletableFuture.runAsync { }` 里，
异常会被 future 静默丢弃——**外部表现是「什么都没发生」**：不报错、也不工作。

正确写法：
```kotlin
try { client.start() } catch (error: Throwable) {          // ✅ 捕 Throwable
    log.error("启动失败: ${error.javaClass.name}: ${error.message}")
    log.error(error.stackTraceToString())                   // ✅ 打完整堆栈
}
```
**任何异步启动路径都必须 catch Throwable 并打堆栈**，否则你会花几个小时排查「为什么没反应」。

---

## 阶段 5：验证（不许跳过）

分层验证，**每一层都要真跑**：

1. **编译**：模块零告警（新增的告警要修掉，别留给 reviewer）
2. **全量重建**：`./gradlew clean build --no-build-cache --rerun-tasks`
   ⚠️ 不加这两个参数，Gradle 会命中缓存秒过，**等于没验证**。
3. **产物自检**：解开 jar 确认
   - 服务端 API **没有**被打进去
   - 该有的第三方库**在**里面
   - `plugin.yml`/`config.yml` 等资源在位
   - ⚠️ **`plugin.yml` 里的版本号真的被替换了**（见下方构建缓存坑）
4. **实机加载**：下载真实服务端 jar，把插件丢进 `plugins/`，启动，确认
   - 插件加载日志出现
   - **零异常**
   - 配置文件按预期生成（**注释还在**）
5. **渲染类功能**：脱离服务端写一个独立 main，用合成数据跑渲染管线，
   **把 PNG 存下来用眼睛看**，并对关键像素做断言（例：耐久条在 >50% 时是绿色
   `(73,214,112)`，损坏时只剩暗色槽底）。
6. **真机联调**：真的进服、真的发消息、真的看渲染图。

### ⚠️ 构建缓存坑：版本号没被替换

`processResources` 里用 `ReplaceTokens` 替换 `@version@` 时，**必须把版本号声明为任务输入**：
```kotlin
tasks.processResources {
    val ver = project.version.toString()
    inputs.property("version", ver)     // ⚠️ 少了这行，只改版本号时任务被判 up-to-date
    filesMatching("plugin.yml") {
        filter(org.apache.tools.ant.filters.ReplaceTokens::class,
               mapOf("tokens" to mapOf("version" to ver)))
    }
}
```
否则会出现「Nukkit 的 jar 是 1.14.0、Spigot 的 jar 还是 1.13.0」这种诡异现象。

---

## 阶段 6：平台无关但常被忽视的坑

这些问题与平台无关，但迁移时一并暴露，顺手修掉：

1. **QQ / 微信等平台的权限模型**：出站消息分「被动回复」与「主动推送」。
   被动回复免费，主动推送往往要单独申请权限。
   **若出站消息一律不带 `msg_id`，会被平台以「主动消息无权限」拒绝，
   表现为机器人完全不回话。** 解决办法：收到消息时记住 `msg_id`，
   之后一段时间内的出站消息都挂上它和递增的 `seq`（每个 `msg_id` 通常限 5 次）。
2. **判断「上游有没有更新」不能用版本号**。本仓库版本可能**高于**上游，
   比版本号会永远判定「无更新」。正确做法是比较 **commit SHA**，
   并用 `git merge-base --is-ancestor <上游sha> HEAD` 判断是否已包含。
3. **读配置时不要每次调用都扫盘**（如每次消息都遍历敏感词目录）。
4. **文本输出里的 `--- | --- |` 全空行会被 QQ markdown 当成表格**渲染成突兀方框，
   包进代码块即可。

---

## 交付要求

- 不动与本迁移无关的文件；共享层的改动要**在提交信息 / PR 描述里单独点明**，
  因为它会**同时影响其他平台**
- 提交信息写清楚：**新功能 / Bug 修复 / 验证方式 / 已知限制**
- 已知限制要诚实列出（例如「某平台未接入统计」「某功能在目标平台无对应插件」）
- ⚠️ **不要 `force push`**；分叉的合并冲突只在**自己的模块路径下**以本地优先，
  其他路径有冲突就 abort 整轮

---

## 一句话总结

> **先侦察 API（看图省事）→ 先跑通构建 → 再补平台核心 → 再啃渲染 →
> 最后死磕依赖边界与异步异常。每一步都要真跑真看，不能靠推断。**
