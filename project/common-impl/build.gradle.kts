val fluxonVersion: String by rootProject.extra
val fluxonPluginVersion: String by rootProject.extra

dependencies {
    compileOnly("ink.ptms:um:1.2.1")
    compileOnly(project(":project:common"))
    testImplementation(project(":project:common"))
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.guava:guava:33.3.1-jre")
    compileOnly("ink.ptms.core:v260100:260100")
    compileOnly("ink.ptms.core:v12110:12110:mapped")
    compileOnly("ink.ptms.core:v12110:12110:universal")
    testImplementation("ink.ptms.core:v12110:12110:mapped")
    testImplementation("ink.ptms.core:v12110:12110:universal")
    compileOnly("org.tabooproject.fluxon:core:$fluxonVersion")
    compileOnly("org.tabooproject.fluxon.plugin:core:$fluxonPluginVersion")
    compileOnly("org.tabooproject.fluxon.plugin:common:$fluxonPluginVersion")
    compileOnly("org.tabooproject.fluxon.plugin:platform-bukkit:$fluxonPluginVersion")
    compileOnly("public:HeadDatabase:1.3.0")
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("org.ow2.asm:asm-util:9.8")
    compileOnly("org.ow2.asm:asm-commons:9.8")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.17.0")
    compileOnly(fileTree("libs"))
}

taboolib {
    subproject = true
}
