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

// 与 :server-Nukkit 的 gatherJar 同一套约定：产物汇集到 build/gather-jar/，
// 发版流水线在那里按 `HuHoBot-*.jar` 通配取产物上传。没有这一步，引擎包只会留在
// addon/GraalJs/build/libs/ 里，自动发的 Release 就只有插件本体，用户装完 .js 脚本
// 会报「未安装 GraalJS 引擎」。
val gatherJar by tasks.registering(Sync::class) {
    group = "build"
    description = "Collects the packaged GraalJS engine into build/gather-jar (removing stale jars)."
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("gather-jar"))

    // 见 server/Nukkit 里同一处注释：三个模块的 Sync 指向同一个目录，必须声明各自负责的
    // 文件名族，否则后跑的会把先跑的产物删掉。语义是「保留除本模块外的所有 HuHoBot-*」。
    preserve {
        include("HuHoBot-*")
        exclude("HuHoBot-Engine-GraalJs-*.jar")
    }

    // 理由同 server/Nukkit：不清掉旧版本 jar，发版就会带上错版本。
    outputs.upToDateWhen { false }
}

tasks.shadowJar {
    archiveFileName.set("HuHoBot-Engine-GraalJs-${project.version}.jar")
    mergeServiceFiles()
    exclude(
        "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
        "module-info.class", "**/module-info.class", "META-INF/versions/**"
    )
    finalizedBy(gatherJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
