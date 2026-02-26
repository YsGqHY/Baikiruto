dependencies {
    compileOnly(project(":project:common"))
    // 如果不需要跨平台，可以在此处引入 Bukkit 核心
    compileOnly("ink.ptms.core:v11903:11903:mapped")
    compileOnly("ink.ptms.core:v11903:11903:universal")
    // Fluxon
    compileOnly("org.tabooproject.fluxon:core:1.5.7")
    compileOnly("org.tabooproject.fluxon.plugin:core:1.0.9")
    compileOnly("org.tabooproject.fluxon.plugin:platform-bukkit:1.0.9")
    // Reflex Remapper
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("org.ow2.asm:asm-util:9.8")
    compileOnly("org.ow2.asm:asm-commons:9.8")
}

// 子模块
taboolib { subproject = true }