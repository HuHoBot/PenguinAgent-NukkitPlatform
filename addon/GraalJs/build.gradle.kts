plugins {
    java
    id("com.gradleup.shadow")
}

// Spigot 的 JavaScript 引擎包。不是一个 Bukkit 插件：没有 plugin.yml，也不注册命令。
// GraalJS 解压后约 37 MB，打进主 jar 会把插件撑到 50 MB 以上，所以和 GraalPy 一样
// 拆出来。构建后把 jar 放到 plugins/HuHoBotPenguin/engines/，主插件第一次加载 .js 时
// 用自己的 classloader 把 GraalJS 读进来。
// 不放这个 jar 时主插件照常运行，只是 .js 脚本会报「未安装 GraalJS 引擎」。

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

// 服务端是 JDK 17，桥接类必须按 17 字节码编译。
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val graal = "24.1.2"

dependencies {
    // 不用 org.graalvm.polyglot:js-community 那个 POM：它的真正引擎是 runtime scope，
    // Gradle 不传递，shadowJar 里会是空壳。js-scriptengine 提供 JSR-223 的 graal.js
    // 引擎名，主插件通过 javax.script.ScriptEngineManager 找到它。
    implementation("org.graalvm.js:js-scriptengine:$graal")
    implementation("org.graalvm.polyglot:polyglot:$graal")
    implementation("org.graalvm.js:js-language:$graal")
    implementation("org.graalvm.truffle:truffle-runtime:$graal")
}

tasks.shadowJar {
    archiveFileName.set("HuHoBot-Engine-GraalJs-${project.version}.jar")
    mergeServiceFiles()
    exclude(
        "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
        "module-info.class", "**/module-info.class", "META-INF/versions/**"
    )
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
