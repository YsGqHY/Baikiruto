package org.tabooproject.baikiruto.impl.script

import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.PrimitiveLoader
import taboolib.common.PrimitiveSettings
import taboolib.common.env.DependencyScope
import taboolib.common.env.JarRelocation
import taboolib.common.env.RuntimeEnv
import taboolib.common.inject.ClassVisitorHandler
import taboolib.common.io.runningClassMap
import taboolib.common.platform.Awake
import java.io.File

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.FluxonTestLoader
 *
 * @author mical
 * @since 2026/1/5 23:56
 */
object FluxonChecker {

    private const val FLUXON_VERSION = "1.6.16"
    private const val FP_VERSION = "1.1.7"

    private var isCentral = false

    @Awake(LifeCycle.CONST)
    fun download() {
        if (Bukkit.getPluginManager().getPlugin("FluxonPlugin") != null) {
            isCentral = true
            return
        }
        val rel = ArrayList<JarRelocation>()
        rel.add(JarRelocation("!org.tabooproject.fluxon".substring(1), "!org.tabooproject.baikiruto.impl.script.fluxon".substring(1)))
        val scope = listOf(DependencyScope.RUNTIME, DependencyScope.COMPILE)
        load("!org.tabooproject.fluxon:core:$FLUXON_VERSION".substring(1), scope, rel)
        load("!org.tabooproject.fluxon:inst-core:$FLUXON_VERSION".substring(1), scope, rel)
        if (!PrimitiveSettings.IS_ISOLATED_MODE) {
            rel.add(JarRelocation(RuntimeEnv.KOTLIN_ID + ".", PrimitiveSettings.getRelocatedKotlinVersion() + "."))
            rel.add(JarRelocation(RuntimeEnv.KOTLIN_COROUTINES_ID + ".", PrimitiveSettings.getRelocatedKotlinCoroutinesVersion() + "."))
            rel.add(JarRelocation(PrimitiveSettings.ID, PrimitiveLoader.TABOOLIB_PACKAGE_NAME))
        }
        load("!org.tabooproject.fluxon.plugin:core:$FP_VERSION".substring(1), scope, rel)
        load("!org.tabooproject.fluxon.plugin:common:$FP_VERSION".substring(1), scope, rel)
        load("!org.tabooproject.fluxon.plugin:platform-bukkit:$FP_VERSION".substring(1), scope, rel)
    }

    private fun load(url: String, scope: List<DependencyScope>, rel: List<JarRelocation>) {
        RuntimeEnv.ENV_DEPENDENCY.loadDependency(
            url,
            File(PrimitiveSettings.FILE_LIBS),
            rel,
            "https://repo.tabooproject.org/repository/releases",
            false,
            false,
            false,
            scope,
            false
        )
    }

    @Awake(LifeCycle.INIT)
    fun init() {
        if (isCentral) return
        runningClassMap.filter { it.key.startsWith("org.tabooproject.baikiruto.impl.script.fluxon") }
            .forEach { (_, clazz) ->
                if (ClassVisitorHandler.checkPlatform(clazz) && ClassVisitorHandler.checkRequires(clazz)) {
                    ClassVisitorHandler.injectAll(clazz)
                }
            }
    }
}