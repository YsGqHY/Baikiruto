@file:Suppress("PropertyName", "SpellCheckingInspection")

taboolib {
    relocate("org.tabooproject.fluxon", "org.tabooproject.baikiruto.impl.script.fluxon")
}

dependencies {
    taboo("org.tabooproject.fluxon:core:1.5.7") { isTransitive = false }
    taboo("org.tabooproject.fluxon:inst-core:1.5.7") { isTransitive = false }
    taboo("org.tabooproject.fluxon.plugin:core:1.0.9") { isTransitive = false }
    taboo("org.tabooproject.fluxon.plugin:platform-bukkit:1.0.9") { isTransitive = false }
}

tasks {
    jar {
        // 构件名
        archiveBaseName.set(rootProject.name)
        // 打包子项目源代码
        rootProject.subprojects.forEach { from(it.sourceSets["main"].output) }
    }
    sourcesJar {
        // 构件名
        archiveBaseName.set(rootProject.name)
        // 打包子项目源代码
        rootProject.subprojects.forEach { from(it.sourceSets["main"].allSource) }
    }
}
