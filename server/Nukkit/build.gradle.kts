plugins {
    java
    kotlin("jvm")
    id("com.gradleup.shadow")
}

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    // Nukkit-MOT（cn.nukkit:Nukkit:MOT-SNAPSHOT）官方仓库。
    maven("https://repo.lanink.cn/repository/maven-public/")
    // Cloudburst Nukkit 官方仓库，作为 MOT 不可用时的备用来源。
    maven("https://repo.opencollab.dev/maven-snapshots/")
    maven("https://jitpack.io")
}

version = rootProject.version

dependencies {
    // 平台无关运行时（含 common-Bot）与其 YAML 读取器。
    implementation(project(":server-AdapterCommon"))

    // Nukkit-MOT 服务端 API：仅编译期使用，不打包进产物。
    compileOnly("cn.nukkit:Nukkit:MOT-SNAPSHOT")

    // Nukkit-MOT 用 log4j2 作为日志后端（MainLogger 是 @Log4j2），
    // 命令输出捕获沿用与 Spigot 适配器相同的 root logger Appender 方案。
    // 版本与 MOT 的 <log4j2.version> 保持一致；运行时由服务端提供，故仅编译期引用。
    compileOnly("org.apache.logging.log4j:log4j-api:2.26.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.26.1")

    // common-Bot 以 implementation 方式引入 fastjson，此处需自行声明才能在模块内直接使用。
    implementation("com.alibaba:fastjson:2.0.32")

    // 扫码登录：控制台二维码渲染（core）+ WebUI 二维码 PNG Base64（javase 的 MatrixToImageWriter）。
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    // 脚本扩展引擎。
    // JS：与 AXDA-ScriptEngine 相同的 GraalJS（MIT，https://github.com/Ruokwok/AXDA-ScriptEngine）。
    //     不在主包里。放在 :addon-GraalJs，与 Spigot 共用，运行时从 plugins/<本插件>/engines/ 加载。
    // LUA：与 NuclearScripting 相同的 Lua 5.4（party.iroiro.luajava）。natives-desktop 是
    //     带分类器的 jar，Gradle 不会当普通依赖传递，所以显式声明 runtimeOnly 再打进包。
    // PY：不在主包里。GraalPy（Python 3）放在 :addon-GraalPy，与 Spigot 共用，运行时从 plugins/<本插件>/engines/ 加载。
    val luajava = "4.1.0"
    implementation("party.iroiro.luajava:luajava:$luajava")
    implementation("party.iroiro.luajava:lua54:$luajava")
    runtimeOnly("party.iroiro.luajava:lua54-platform:$luajava:natives-desktop")

    implementation(kotlin("stdlib"))
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))
kotlin.jvmToolchain(17)

// 背包渲染用的 Faithful 贴图 / 护甲纹饰 / 默认皮肤资源（约 11 MB、2100+ 文件）直接复用
// Spigot 模块已入库的那一份，避免在仓库里重复存放同一套美术资源。
val inventoryAssetsDir = layout.buildDirectory.dir("generated/inventory-assets")

val prepareInventoryAssets by tasks.registering(Sync::class) {
    group = "build"
    description = "Copies the shared inventory/armor/skin assets required by the renderer."
    from(rootProject.file("server/Spigot/src/main/resources/inventory")) { into("inventory") }
    into(inventoryAssetsDir)
}

sourceSets.named("main") {
    resources.srcDir(inventoryAssetsDir)
}

tasks.processResources {
    dependsOn(prepareInventoryAssets)

    val pluginVersion = rootProject.version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        filter(org.apache.tools.ant.filters.ReplaceTokens::class, mapOf(
            "tokens" to mapOf("version" to pluginVersion)
        ))
    }
}

// build/gather-jar/ 是所有发布产物的汇集目录，发版流水线按 `HuHoBot-*.jar` 通配取产物。
// :addon-GraalJs 和 :addon-GraalPy 也会往同一个目录放引擎包，每个模块各管自己那一族文件。
//
// ⚠️ 必须用 Sync 而不是 Copy：产物文件名带版本号，版本号一升文件名就变了，而 Copy 只往
// 目标目录里合并、从不清理，于是新旧两个 jar 会同时留在那儿；流水线跑的又是不带 clean 的
// `./gradlew build`，会把上一版的 jar 一起传上 Release。
val gatherJar by tasks.registering(Sync::class) {
    group = "build"
    description = "Collects the packaged Nukkit plugin into build/gather-jar (removing stale jars)."
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("gather-jar"))

    // Sync 默认会删掉目标目录里「不是本次执行产出」的所有文件，而另外两个模块也往这里放
    // 引擎包 —— 三个 Sync 指向同一个目录就会互相删对方的 jar，谁最后跑谁赢。preserve 用来
    // 声明「这些不是我负责的文件，别动」。这里写成「保留所有 HuHoBot-*，除了本模块那一族」，
    // 而不是逐个列举兄弟模块的产物名，是为了将来再加引擎模块时不必回头改这里。
    preserve {
        include("HuHoBot-*")
        exclude("HuHoBot-Penguin_Nukkit-*.jar")
    }

    // 光换成 Sync 还不够：Gradle 的 up-to-date 检查只比对自己产出的文件，目标目录里多出来的
    // 旧 jar 不算变化，实测任务会被判定 UP-TO-DATE 直接跳过、旧文件原地不动。这个任务的全部
    // 意义就是维持「目录里只有当前版本」，跳过它没有任何收益，代价只是一次本地文件同步。
    outputs.upToDateWhen { false }
}

tasks.shadowJar {
    archiveFileName.set("HuHoBot-Penguin_Nukkit-${project.version}.jar")
    finalizedBy(gatherJar)

    // Nukkit 的服务端 classloader 是「父优先」的，下列库服务端已自带，打包进来只会增大体积、
    // 并且永远不会被加载，所以排除。
    //
    // 注意：logback **不能**排除。QQ SDK 的 Starter 静态初始化块直接引用了
    // ch.qos.logback.core.Context（链接期硬依赖），缺了会抛 NoClassDefFoundError，
    // 表现为「机器人一直连不上、且没有任何报错」。MOT 不提供 logback；SLF4J 的 provider
    // 是在服务端 classloader 上发现的，所以打包 logback 不会和 MOT 的 log4j-slf4j2-impl 抢绑定。
    dependencies {
        exclude(dependency("com.google.code.gson:gson:.*"))
        exclude(dependency("org.yaml:snakeyaml:.*"))
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }

    exclude("module-info.class", "**/module-info.class", "META-INF/versions/**")

    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build { dependsOn(tasks.shadowJar) }
