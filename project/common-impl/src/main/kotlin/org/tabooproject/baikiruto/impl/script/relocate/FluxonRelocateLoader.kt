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
}