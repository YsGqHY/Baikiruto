@file:Suppress("PropertyName", "SpellCheckingInspection")

import io.izzel.taboolib.gradle.*
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.30" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
}

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
        }
        env {
            install(Basic, Bukkit, BukkitUtil, BukkitNMS, BukkitNMSUtil, BukkitUI, BukkitHook)
            install(Database, DatabasePlayer)
            install(CommandHelper)
        }
        version { taboolib = "6.2.4-99fb800" }
    }

    // 仓库
    repositories {
        mavenCentral()
        maven("https://nexus.maplex.top/repository/maven-public/")
    }
    // 依赖
    dependencies {
        compileOnly(kotlin("stdlib"))
        testImplementation(kotlin("test"))
    }

    // 编译配置
    java {
        withSourcesJar()
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf("-Xjvm-default=all", "-Xextended-compiler-checks")
        }
    }
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

gradle.buildFinished {
    buildDir.deleteRecursively()
}
