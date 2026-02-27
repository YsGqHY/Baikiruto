dependencies {
    compileOnly(project(":project:common"))
    compileOnly("ink.ptms.core:v12110:12110:mapped")
    compileOnly("ink.ptms.core:v12110:12110:universal")
    compileOnly("org.tabooproject.fluxon:core:1.5.7")
    compileOnly("org.tabooproject.fluxon.plugin:core:1.0.9")
    compileOnly("org.tabooproject.fluxon.plugin:platform-bukkit:1.0.9")
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("org.ow2.asm:asm-util:9.8")
    compileOnly("org.ow2.asm:asm-commons:9.8")
}

taboolib { subproject = true }
