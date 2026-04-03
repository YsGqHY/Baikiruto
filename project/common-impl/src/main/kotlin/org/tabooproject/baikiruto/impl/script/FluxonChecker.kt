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
    private const val FP_VERSION = "1.1.4"
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

    private fun buildCoreRelocations(): ArrayList<JarRelocation> {
        return arrayListOf(
            JarRelocation(
                fluxonGroupId(),
                relocatedFluxonPackage()
            )
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
            setIgnoreException(false)
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
