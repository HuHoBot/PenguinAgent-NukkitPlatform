plugins {
    java
    id("com.gradleup.shadow")
}

// GraalPy 引擎包。不是一个 Bukkit 插件：没有 plugin.yml，也不注册命令。
// 构建后把 jar 放到 plugins/HuHoBotPenguin/engines/，主插件启动时用自己的
// classloader 把里面的引擎类加载进来，.py 脚本就能用 GraalPy（Python 3）执行。
// 不放这个 jar 时，主插件照常运行，只是 .py 脚本会报「未安装 GraalPy 引擎」。

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

// GraalPy 的 polyglot/truffle 运行时需要 Java 17；主插件仍是 Java 8，
// 所以这里的 toolchain 只作用于引擎包自己。
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val graal = "24.1.2"

dependencies {
    // polyglot 与 truffle 运行时一起打进引擎包。主插件已经不再携带它们：
    // GraalJS 与 GraalPy 都拆成了独立 jar，各带一份运行时，互不依赖。
    implementation("org.graalvm.polyglot:polyglot:$graal")
    implementation("org.graalvm.python:python-language:$graal")
    implementation("org.graalvm.python:python-resources:$graal")
    implementation("org.graalvm.truffle:truffle-runtime:$graal")
}

// 与 :server-Nukkit 的 gatherJar 同一套约定：产物汇集到 build/gather-jar/，
// 发版流水线在那里按 `HuHoBot-*.jar` 通配取产物上传。没有这一步，引擎包只会留在
// addon/GraalPy/build/libs/ 里，自动发的 Release 就只有插件本体，用户装完 .py 脚本
// 会报「未安装 GraalPy 引擎」。
val gatherJar by tasks.registering(Sync::class) {
    group = "build"
    description = "Collects the packaged GraalPy engine into build/gather-jar (removing stale jars)."
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("gather-jar"))

    // 见 server/Nukkit 里同一处注释：三个模块的 Sync 指向同一个目录，必须声明各自负责的
    // 文件名族，否则后跑的会把先跑的产物删掉。语义是「保留除本模块外的所有 HuHoBot-*」。
    preserve {
        include("HuHoBot-*")
        exclude("HuHoBot-Engine-GraalPy-*.jar")
    }

    // 理由同 server/Nukkit：不清掉旧版本 jar，发版就会带上错版本。
    outputs.upToDateWhen { false }
}

tasks.shadowJar {
    archiveFileName.set("HuHoBot-Engine-GraalPy-${project.version}.jar")
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
