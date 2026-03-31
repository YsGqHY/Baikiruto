dependencies {
    compileOnly("ink.ptms:um:1.2.1")
    compileOnly(project(":project:common"))
    testImplementation(project(":project:common"))
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.guava:guava:33.3.1-jre")
    compileOnly("ink.ptms.core:v12110:12110:mapped")
    compileOnly("ink.ptms.core:v12110:12110:universal")
    testImplementation("ink.ptms.core:v12110:12110:mapped")
    testImplementation("ink.ptms.core:v12110:12110:universal")
    compileOnly("org.tabooproject.fluxon:core:1.6.24")
    compileOnly("org.tabooproject.fluxon.plugin:core:1.1.4")
    compileOnly("org.tabooproject.fluxon.plugin:common:1.1.4")
    compileOnly("org.tabooproject.fluxon.plugin:platform-bukkit:1.1.4")
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
