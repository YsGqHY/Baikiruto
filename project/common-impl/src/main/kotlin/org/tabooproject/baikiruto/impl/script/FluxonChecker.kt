package org.tabooproject.baikiruto.impl.script

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.impl.log.BaikirutoLog
import taboolib.common.LifeCycle
import taboolib.common.PrimitiveLoader
import taboolib.common.PrimitiveSettings
import taboolib.common.env.DependencyScope
import taboolib.common.env.JarRelocation
import taboolib.common.env.RuntimeEnv
import taboolib.common.env.RuntimeEnvDependency
import taboolib.common.inject.ClassVisitorHandler
import taboolib.common.io.runningClassMap
import taboolib.common.platform.Awake
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
        RuntimeEnvDependency().loadDependency(
            url,
            java.io.File(PrimitiveSettings.FILE_LIBS),
            rel,
            FLUXON_REPOSITORY,
            true,   // ignoreOptional
            true,   // ignoreException
            true,   // transitive
            scope,
            false   // external（需要被类扫描器扫到）
        )
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
        val fluxonClasses = runningClassMap.filter { it.key.startsWith(relocatedFluxonPackage()) }
        // 预扫描：收集所有标注了 @Requires 但条件不满足的 Fluxon 类（JVM 内部名）
        val excludedClasses = collectExcludedClasses(fluxonClasses.values)
        fluxonClasses.forEach { (_, clazz) ->
            if (ClassVisitorHandler.checkPlatform(clazz)
                && checkFluxonRequires(clazz)
                && !referencesExcludedClass(clazz, excludedClasses)
            ) {
                ClassVisitorHandler.injectAll(clazz)
            }
        }
    }

    /**
     * 检查 Fluxon 类是否满足 @Requires 条件。
     */
    private fun checkFluxonRequires(clazz: taboolib.library.reflex.ReflexClass): Boolean {
        val structure = clazz.structure ?: return true
        if (!structure.isAnnotationPresent(taboolib.common.Requires::class.java)) return true
        return ClassVisitorHandler.checkRequires(clazz)
    }

    /**
     * 收集所有标注了 @Requires 但条件不满足的 Fluxon 类的 JVM 内部名。
     */
    private fun collectExcludedClasses(classes: Collection<taboolib.library.reflex.ReflexClass>): Set<String> {
        val excluded = mutableSetOf<String>()
        for (clazz in classes) {
            val structure = clazz.structure ?: continue
            if (structure.isAnnotationPresent(taboolib.common.Requires::class.java) && !ClassVisitorHandler.checkRequires(clazz)) {
                excluded.add((clazz.name ?: continue).replace('.', '/'))
            }
        }
        return excluded
    }

    /**
     * 检查类的常量池是否引用了被排除的类。
     *
     * 解决自身 @Requires 通过、但方法体内间接触发了不兼容类的问题。
     * 例如 FnBoat.init() 引用 FnBoatType，而 FnBoatType 的 @Requires 不满足。
     * ClassVisitorHandler 内部 catch 后直接 printStackTrace，外部 try-catch 无法拦截，
     * 因此必须在 injectAll 之前通过常量池扫描提前过滤。
     */
    private fun referencesExcludedClass(clazz: taboolib.library.reflex.ReflexClass, excluded: Set<String>): Boolean {
        if (excluded.isEmpty()) return false
        val resName = (clazz.name ?: return false).replace('.', '/') + ".class"
        return try {
            val bytes = javaClass.classLoader.getResourceAsStream(resName)?.use { it.readBytes() } ?: return false
            constantPoolClasses(bytes).any { it in excluded }
        } catch (_: Throwable) {
            false
        }
    }

    /** 从 class 文件字节码中提取常量池里所有 CONSTANT_Class 引用的类名。 */
    private fun constantPoolClasses(b: ByteArray): Set<String> {
        if (b.size < 10) return emptySet()
        var p = 8
        val n = u2(b, p); p += 2
        val utf = HashMap<Int, String>(n)
        val cls = mutableListOf<Int>()
        var i = 1
        while (i < n && p < b.size) {
            when (b[p++].toInt() and 0xFF) {
                1    -> { val len = u2(b, p); p += 2; utf[i] = String(b, p, len, Charsets.UTF_8); p += len }
                7    -> { cls += u2(b, p); p += 2 }
                8, 16 -> p += 2
                3, 4  -> p += 4
                5, 6  -> { p += 8; i++ }
                9, 10, 11, 12, 17, 18 -> p += 4
                15   -> p += 3
                19, 20 -> p += 2
                else -> return emptySet()
            }
            i++
        }
        return cls.mapNotNullTo(mutableSetOf()) { utf[it] }
    }

    private fun u2(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF shl 8) or (b[o + 1].toInt() and 0xFF)
}
