dependencies {
    compileOnly(project(":project:common"))
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("ink.ptms.core:v12110:12110:mapped")
    compileOnly("ink.ptms.core:v12110:12110:universal")
    testImplementation(project(":project:common"))
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("ink.ptms.core:v12110:12110:mapped")
    testImplementation("ink.ptms.core:v12110:12110:universal")
}

taboolib { subproject = true }
