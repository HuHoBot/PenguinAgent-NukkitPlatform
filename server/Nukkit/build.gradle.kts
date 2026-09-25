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

    // 首次启动扫码登录：控制台二维码渲染。
    implementation("com.google.zxing:core:3.5.3")

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

val gatherJar by tasks.registering(Copy::class) {
    group = "build"
    description = "Collects the packaged Nukkit plugin into build/gather-jar."
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("gather-jar"))
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

    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build { dependsOn(tasks.shadowJar) }
