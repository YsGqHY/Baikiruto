dependencies {
    compileOnly(project(":project:common"))
    compileOnly(project(":project:common-impl"))
    compileOnly("ink.ptms.core:v12110:12110:mapped")
    compileOnly("ink.ptms.core:v12110:12110:universal")
}

taboolib { subproject = true }
