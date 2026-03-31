package org.tabooproject.baikiruto.impl.script.relocate

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.impl.script.DefaultScriptHandler
import org.tabooproject.baikiruto.impl.script.handler.Fluxon
import org.tabooproject.baikiruto.impl.script.handler.FluxonHandler
import taboolib.common.LifeCycle
import taboolib.common.inject.ClassVisitor
import taboolib.common.inject.ClassVisitorHandler
import taboolib.common.io.runningClassMapInJar
import taboolib.common.platform.Awake
import taboolib.library.reflex.ReflexClass
import kotlin.collections.iterator

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.relocate.FluxonRelocateLoader
 *
 * @author mical
 * @since 2026/1/3 14:02
 */
object FluxonRelocateLoader {

    private var propertySetted = false
    var needToTranslate = false

    @Awake(LifeCycle.CONST)
    fun init() {
        if (!propertySetted) {
            if (Bukkit.getServer().pluginManager.getPlugin("FluxonPlugin") != null) {
                propertySetted = true
                needToTranslate = true
            } else {
                DefaultScriptHandler.fluxonHandler = Fluxon
                propertySetted = true
            }
        }
        if (needToTranslate) {
            for ((_, clazz) in runningClassMapInJar) {
                if (clazz.structure.isAnnotationPresent(FluxonRelocate::class.java)) {
                    val newClazz = ReflexClass.of(AsmClassTranslation.createNewClass(clazz.name!!))
                    ClassVisitorHandler.injectAll(newClazz)
                    // FIXME 判断有点粗糙，有待优化
                    if (clazz.name == "org.tabooproject.baikiruto.impl.script.handler.Fluxon") {
                        ClassVisitor.findInstance(newClazz).let { DefaultScriptHandler.fluxonHandler = it as FluxonHandler }
                    }
                }
            }
        }
    }

    /**
     * 在 ACTIVE 阶段从共享注册表导入 Baikiruto 导出的函数到 FluxonPlugin 的 Runtime。
     * 此时 FunctionMythicMobs 等已在 ENABLE 阶段完成导出，可以安全导入。
     */
    @Awake(LifeCycle.ACTIVE)
    fun importSharedFunctions() {
        if (!needToTranslate) return
        try {
            // 必须通过 FluxonPlugin 的 ClassLoader 加载，避免命中 Baikiruto relocate 后的类
            val fluxonPlugin = Bukkit.getServer().pluginManager.getPlugin("FluxonPlugin") ?: return
            val cl = fluxonPlugin.javaClass.classLoader
            val fluxonRuntimeClass = Class.forName("org.tabooproject.fluxon.runtime.FluxonRuntime", true, cl)
            val runtime = fluxonRuntimeClass.getMethod("getInstance").invoke(null)
            fluxonRuntimeClass.getMethod("importAllSharedFunctions", String::class.java).invoke(runtime, "Baikiruto")
        } catch (_: Throwable) {
            // FluxonPlugin 的 Runtime 不可用时静默忽略
        }
    }
}