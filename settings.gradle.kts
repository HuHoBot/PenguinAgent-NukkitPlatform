pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("plugin.lombok") version "2.2.20"
    }
}

// 自动下载缺失的 JDK 工具链：common-Bot 要求 JDK 8，server-Nukkit 要求 JDK 17。
// 本机若未安装对应 JDK，Gradle 会按此解析器自动拉取，避免 "No matching toolchains found"。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":common-Bot")
project(":common-Bot").projectDir = file("common/Bot")

include(":server-AdapterCommon")
project(":server-AdapterCommon").projectDir = file("server/AdapterCommon")

include(":server-Spigot")
project(":server-Spigot").projectDir = file("server/Spigot")

// Allay / Proxy 源码仍保留在仓库中，但不参与构建。
//
// include(":server-Allay")
// project(":server-Allay").projectDir = file("server/Allay")
//
// include(":server-Proxy")
// project(":server-Proxy").projectDir = file("server/Proxy")

include(":server-Nukkit")
project(":server-Nukkit").projectDir = file("server/Nukkit")

// 可选扩展（addon）：不进主插件产物，需要单独构建后放进对应插件的 engines/。
//   ./gradlew :addon-SexPhoto:build      Nukkit 色图扩展
//   ./gradlew :addon-GraalJs:shadowJar   GraalJS 引擎，Nukkit 与 Spigot 共用
//   ./gradlew :addon-GraalPy:shadowJar   GraalPy 引擎，Nukkit 与 Spigot 共用
include(":addon-SexPhoto")
project(":addon-SexPhoto").projectDir = file("addon/SexPhoto")

include(":addon-GraalJs")
project(":addon-GraalJs").projectDir = file("addon/GraalJs")

include(":addon-GraalPy")
project(":addon-GraalPy").projectDir = file("addon/GraalPy")

rootProject.name = "HuHoBotPenguin-NukkitPlatform"
