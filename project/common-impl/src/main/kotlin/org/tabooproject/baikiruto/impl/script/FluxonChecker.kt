package org.tabooproject.baikiruto.impl.script

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.impl.log.BaikirutoLog
import taboolib.common.LifeCycle
import taboolib.common.PrimitiveLoader
import taboolib.common.PrimitiveSettings
import taboolib.common.env.DependencyScope
import taboolib.common.env.JarRelocation
import taboolib.common.env.RuntimeEnv
import taboolib.common.env.legacy.Artifact
import taboolib.common.env.legacy.Dependency
import taboolib.common.env.legacy.DependencyDownloader
import taboolib.common.env.legacy.Repository
import taboolib.common.inject.ClassVisitorHandler
import taboolib.common.io.runningClassMap
import taboolib.common.platform.Awake
import java.io.File
import java.util.Base64

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.FluxonTestLoader
 *
 * @author mical
 * @since 2026/1/5 23:56
 */
object FluxonChecker {

    private const val FLUXON_VERSION = "1.6.24"
    private const val FP_VERSION = "1.1.8"
    private const val FLUXON_REPOSITORY = "https://repo.tabooproject.org/repository/releases"
    private const val MAVEN_CENTRAL_REPOSITORY = "https://repo.maven.apache.org/maven2"
    private const val BUNDLED_FLUXON_PLUGIN_CLASS = "org.tabooproject.baikiruto.impl.script.fluxon.FluxonPlugin"
    private const val BUNDLED_FLUXON_RUNTIME_CLASS = "org.tabooproject.baikiruto.impl.script.fluxon.runtime.FluxonRuntime"

    enum class Source(val id: String) {
        NONE("NONE"),
        EXTERNAL_PLUGIN("EXTERNAL_PLUGIN"),
        BUNDLED("BUNDLED"),
        RUNTIME_DOWNLOADED("RUNTIME_DOWNLOADED"),
        UNAVAILABLE("UNAVAILABLE")
    }

    @Volatile
    private var source: Source = Source.NONE

    @Volatile
    private var startupFailure: Throwable? = null

    val isCentral: Boolean
        get() = source == Source.EXTERNAL_PLUGIN

    fun isReady(): Boolean {
        return source == Source.EXTERNAL_PLUGIN || source == Source.BUNDLED || source == Source.RUNTIME_DOWNLOADED
    }

    fun isUnavailable(): Boolean {
        return source == Source.UNAVAILABLE
    }

    fun isBundledAvailable(): Boolean {
        return isClassAvailable(BUNDLED_FLUXON_PLUGIN_CLASS) || isClassAvailable(BUNDLED_FLUXON_RUNTIME_CLASS)
    }

    fun sourceId(): String {
        return source.id
    }

    fun startupFailureMessage(): String? {
        return startupFailure?.message
    }

    @Awake(LifeCycle.CONST)
    fun download() {
        if (Bukkit.getPluginManager().getPlugin("FluxonPlugin") != null) {
            source = Source.EXTERNAL_PLUGIN
            return
        }
        if (isBundledAvailable()) {
            source = Source.BUNDLED
            return
        }
        try {
            val scope = listOf(DependencyScope.RUNTIME, DependencyScope.COMPILE)
            val coreRelocations = buildCoreRelocations()
            load(fluxonCoordinate("core", FLUXON_VERSION), scope, coreRelocations)
            load(fluxonCoordinate("inst-core", FLUXON_VERSION), scope, coreRelocations)

            val pluginRelocations = ArrayList(coreRelocations)
            if (!PrimitiveSettings.IS_ISOLATED_MODE) {
                pluginRelocations.add(JarRelocation(RuntimeEnv.KOTLIN_ID + ".", PrimitiveSettings.getRelocatedKotlinVersion() + "."))
                pluginRelocations.add(JarRelocation(RuntimeEnv.KOTLIN_COROUTINES_ID + ".", PrimitiveSettings.getRelocatedKotlinCoroutinesVersion() + "."))
                pluginRelocations.add(JarRelocation(PrimitiveSettings.ID, PrimitiveLoader.TABOOLIB_PACKAGE_NAME))
            }
            load(fluxonPluginCoordinate("core", FP_VERSION), scope, pluginRelocations)
            load(fluxonPluginCoordinate("common", FP_VERSION), scope, pluginRelocations)
            load(fluxonPluginCoordinate("platform-bukkit", FP_VERSION), scope, pluginRelocations)

            source = if (isBundledAvailable()) {
                Source.RUNTIME_DOWNLOADED
            } else {
                Source.UNAVAILABLE
            }
            if (source == Source.UNAVAILABLE) {
                BaikirutoLog.fluxonBootstrapFailed(
                    "Fluxon runtime download finished but relocated runtime classes are still unavailable. source=${source.id}"
                )
            }
        } catch (ex: Throwable) {
            source = Source.UNAVAILABLE
            startupFailure = ex
            BaikirutoLog.fluxonBootstrapFailed(
                "Unable to prepare Fluxon runtime. source=RUNTIME_DOWNLOAD, repositories=$FLUXON_REPOSITORY,$MAVEN_CENTRAL_REPOSITORY, cause=${ex.message}"
            )
        }
    }

    /**
     * 构建 Fluxon 运行时下载的 JAR 重定向规则。
     *
     * Fluxon 的传递依赖中包含大量公共库（guava → jsr305/checker-qual/errorprone 等），
     * 如果不重定向，这些类会被注入到 Baikiruto 的类加载器中，导致其他插件
     * （如 LuckPerms、CMILib）意外从 Baikiruto 加载到 javax.annotation 等类。
     *
     * 注意：guava/gson/fastutil 是服务端自带的，不能重定向，否则会导致类型不兼容。
     */
    private fun buildCoreRelocations(): ArrayList<JarRelocation> {
        val prefix = "${relocatedFluxonPackage()}.libs."
        return arrayListOf(
            // Fluxon 自身
            JarRelocation(fluxonGroupId(), relocatedFluxonPackage()),
            // guava 传递依赖中的注解库
            JarRelocation("javax.annotation.", "${prefix}javax.annotation."),
            JarRelocation("org.checkerframework.", "${prefix}org.checkerframework."),
            JarRelocation("com.google.errorprone.", "${prefix}com.google.errorprone."),
            JarRelocation("com.google.j2objc.", "${prefix}com.google.j2objc."),
            JarRelocation("com.google.thirdparty.", "${prefix}com.google.thirdparty."),
            // ASM
            JarRelocation("org.objectweb.asm.", "${prefix}org.objectweb.asm."),
            // JetBrains Annotations
            JarRelocation("org.jetbrains.annotations.", "${prefix}org.jetbrains.annotations."),
            JarRelocation("org.intellij.lang.annotations.", "${prefix}org.intellij.lang.annotations."),
            // JLine / JNA / Jansi
            JarRelocation("org.jline.", "${prefix}org.jline."),
            JarRelocation("org.fusesource.jansi.", "${prefix}org.fusesource.jansi."),
            JarRelocation("com.sun.jna.", "${prefix}com.sun.jna."),
        )
    }

    private fun load(url: String, scope: List<DependencyScope>, rel: List<JarRelocation>) {
        val artifact = Artifact(url)
        val dependency = Dependency(
            artifact.groupId,
            artifact.artifactId,
            artifact.version,
            DependencyScope.RUNTIME
        ).apply {
            setType(artifact.extension)
            setExternal(false)
        }
        val downloader = DependencyDownloader(File(PrimitiveSettings.FILE_LIBS), rel).apply {
            addRepository(Repository(FLUXON_REPOSITORY))
            addRepository(Repository(MAVEN_CENTRAL_REPOSITORY))
            setIgnoreOptional(true)
            // 传递依赖中可能包含运行时不需要的构建工具（如 jansi → picocli-codegen），
            // 这些制品在部分仓库中不可用，下载失败不应阻断 Fluxon 初始化
            setIgnoreException(true)
            setDependencyScopes(scope)
            setTransitive(true)
        }
        downloader.injectClasspath(downloader.loadDependency(downloader.repositories.toList(), dependency))
    }

    private fun fluxonCoordinate(artifactId: String, version: String): String {
        return "${fluxonGroupId()}:$artifactId:$version"
    }

    private fun fluxonPluginCoordinate(artifactId: String, version: String): String {
        return "${fluxonPluginGroupId()}:$artifactId:$version"
    }

    private fun fluxonGroupId(): String {
        return decode("b3JnLnRhYm9vcHJvamVjdC5mbHV4b24=")
    }

    private fun fluxonPluginGroupId(): String {
        return fluxonGroupId() + ".plugin"
    }

    private fun relocatedFluxonPackage(): String {
        return listOf("org", "tabooproject", "baikiruto", "impl", "script", "fluxon").joinToString(".")
    }

    private fun decode(value: String): String {
        return String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }

    private fun isClassAvailable(name: String): Boolean {
        return runCatching {
            Class.forName(name, false, javaClass.classLoader)
            true
        }.getOrDefault(false)
    }

    @Awake(LifeCycle.INIT)
    fun init() {
        if (isCentral || isUnavailable()) return
        runningClassMap.filter { it.key.startsWith(relocatedFluxonPackage()) }
            .forEach { (_, clazz) ->
                if (ClassVisitorHandler.checkPlatform(clazz) && ClassVisitorHandler.checkRequires(clazz)) {
                    ClassVisitorHandler.injectAll(clazz)
                }
            }
    }
}
