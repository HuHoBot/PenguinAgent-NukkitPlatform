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

// 自动下载缺失的 JDK 工具链：common-Bot / server-Spigot 要求 JDK 8，server-Nukkit 要求 JDK 17。
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

// 当前构建 Spigot 与 Nukkit 两个平台（Nukkit 目标为 Nukkit-MOT）。
include(":server-Nukkit")
project(":server-Nukkit").projectDir = file("server/Nukkit")

// 尚未适配；如需构建可取消下方注释。
// include(":server-Allay")
// project(":server-Allay").projectDir = file("server/Allay")
//
// include(":server-Proxy")
// project(":server-Proxy").projectDir = file("server/Proxy")

rootProject.name = "HuHoBotPenguin"
