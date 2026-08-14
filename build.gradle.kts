//@file:Suppress("PropertyName", "SpellCheckingInspection")

import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.36" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
}

val fluxonVersion by extra("1.6.24")
val fluxonPluginVersion by extra("1.1.8")

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.izzel.taboolib")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    // TabooLib 配置
    // 这里的配置是全局的，如果你的项目有多个模块，这里的配置会被所有模块共享
    // 为了降低理解难度，使用这种更加无脑的配置方式
    configure<TabooLibExtension> {
        description {
            name(rootProject.name)
            dependencies {
                name("RoseLoot").optional(true)
            }
        }
        env {
            install(Basic, Bukkit, BukkitUtil, BukkitNMS, BukkitNMSUtil, BukkitUI, BukkitHook)
            install(Database, DatabasePlayer)
            install(CommandHelper)
            install(I18n, MinecraftChat)
            install(Metrics)
            
            forceDownloadInDev = false
            enableLegacyDependencyResolver = true
        }
        version {
            taboolib = "6.3.0-75b18a2"
        }
    }

    // 仓库
    repositories {
        mavenCentral()
        maven("https://nexus.maplex.top/repository/maven-public/")
        maven("https://repo.rosewooddev.io/repository/public/")
    }
    // 依赖
    dependencies {
        compileOnly(kotlin("stdlib"))
        testImplementation(kotlin("test"))
    }

    // 编译配置
    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf("-Xjvm-default=all", "-Xextended-compiler-checks")
        }
    }
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

tasks.register("publishLocal") {
    group = "publishing"
    description = "Publish source/buildApi/plugin artifacts to mavenLocal."
    dependsOn(":plugin:publishMavenPublicationToMavenLocal")
}

tasks.register("publishAeolian") {
    group = "publishing"
    description = "Publish source/buildApi/plugin artifacts to Aeolian releases repository."
    dependsOn(":plugin:publishMavenPublicationToAeolianReleasesRepository")
}

gradle.buildFinished {
    buildDir.deleteRecursively()
}
