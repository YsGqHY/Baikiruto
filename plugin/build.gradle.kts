@file:Suppress("PropertyName", "SpellCheckingInspection")

import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    `maven-publish`
}

taboolib {
    relocate("org.tabooproject.fluxon", "org.tabooproject.baikiruto.impl.script.fluxon")
}

dependencies {
    taboo("org.tabooproject.fluxon:core:1.5.7") { isTransitive = false }
    taboo("org.tabooproject.fluxon:inst-core:1.5.7") { isTransitive = false }
    taboo("org.tabooproject.fluxon.plugin:core:1.0.9") { isTransitive = false }
    taboo("org.tabooproject.fluxon.plugin:platform-bukkit:1.0.9") { isTransitive = false }
}

val pluginBaseName = rootProject.name
val pluginJar = layout.buildDirectory.file("libs/${pluginBaseName}-${project.version}.jar")
val buildApiJar = layout.buildDirectory.file("libs/${pluginBaseName}-${project.version}-api.jar")
val gradleWrapper = if (System.getProperty("os.name").startsWith("Windows")) "gradlew.bat" else "./gradlew"
val buildApiGradleUserHome = layout.buildDirectory.dir("publish-gradle-user")

val preparePublishSourceAndMain by tasks.registering {
    group = "publishing"
    description = "Build source and plugin body artifacts."
    dependsOn(rootProject.tasks.named("build"))
}

val preparePublishBuildApi by tasks.registering(Exec::class) {
    group = "publishing"
    description = "Build buildApi artifact with gradlew taboolibBuildApi -PDeleteCode."
    workingDir = rootProject.projectDir
    commandLine(
        gradleWrapper,
        "--no-daemon",
        "-g",
        buildApiGradleUserHome.get().asFile.absolutePath,
        "taboolibBuildApi",
        "-PDeleteCode"
    )
    dependsOn(preparePublishSourceAndMain)
}

val preparePublishArtifacts by tasks.registering {
    group = "publishing"
    description = "Prepare source/buildApi/plugin body artifacts for publish."
    dependsOn(preparePublishBuildApi)
}

tasks {
    jar {
        archiveBaseName.set(rootProject.name)
        rootProject.subprojects.forEach { from(it.sourceSets["main"].output) }
    }
    sourcesJar {
        archiveBaseName.set(rootProject.name)
        rootProject.subprojects.forEach { from(it.sourceSets["main"].allSource) }
    }
}

publishing {
    repositories {
        mavenLocal()
        maven {
            name = "aeolianReleases"
            url = uri("http://repo.aeoliancloud.com/repository/releases")
            isAllowInsecureProtocol = true
            credentials {
                username = (project.findProperty("aeolianUsername") as String?) ?: ""
                password = (project.findProperty("aeolianPassword") as String?) ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = rootProject.name
            version = project.version.toString()
            artifact(pluginJar)
            artifact(buildApiJar) {
                classifier = "api"
            }
            artifact(tasks.named("sourcesJar"))
        }
    }
}

tasks.withType<PublishToMavenLocal>().configureEach {
    dependsOn(preparePublishArtifacts)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(preparePublishArtifacts)
}
