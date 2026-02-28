dependencies {
    taboo("ink.ptms:um:1.1.5") { isTransitive = false }
    compileOnly(project(":project:common"))
    testImplementation(project(":project:common"))
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.guava:guava:33.3.1-jre")
    compileOnly("ink.ptms.core:v12110:12110:mapped")
    compileOnly("ink.ptms.core:v12110:12110:universal")
    testImplementation("ink.ptms.core:v12110:12110:mapped")
    testImplementation("ink.ptms.core:v12110:12110:universal")
    compileOnly("org.tabooproject.fluxon:core:1.5.7")
    compileOnly("org.tabooproject.fluxon.plugin:core:1.0.9")
    compileOnly("org.tabooproject.fluxon.plugin:platform-bukkit:1.0.9")
    compileOnly("public:HeadDatabase:1.3.0")
    compileOnly("org.ow2.asm:asm:9.8")
    compileOnly("org.ow2.asm:asm-util:9.8")
    compileOnly("org.ow2.asm:asm-commons:9.8")
    compileOnly(fileTree("libs"))
}

taboolib {
    subproject = true
    relocate("ink.ptms.um","${group}.um")
}
