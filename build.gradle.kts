plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("com.gradleup.shadow") version "8.3.11" apply false
}

// 版本号唯一来源是根目录 gradle.properties。
// ⚠️ 不要在这里再写死版本号：两处各写一份时，自动化只改得动其中一处，
// 会出现「代码里是新版本、产物里是旧版本」的不一致（plugin.yml 的 @version@ 就是从这里取的）。
val pluginVersion: String = providers.gradleProperty("version").get()

allprojects {
    group = "cn.huohuas001"
    version = pluginVersion

    repositories {
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}
