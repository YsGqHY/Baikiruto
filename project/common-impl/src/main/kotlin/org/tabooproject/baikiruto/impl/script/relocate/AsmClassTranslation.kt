package org.tabooproject.baikiruto.impl.script.relocate

import org.bukkit.Bukkit
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import taboolib.common.TabooLib
import taboolib.common.platform.function.debug
import taboolib.common.util.execution
import taboolib.common.util.t
import taboolib.library.reflex.Reflex.Companion.invokeMethod

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.relocate.AsmClassTranslation
 *
 * @author mical
 * @since 2026/1/3 14:05
 */
object AsmClassTranslation {

    const val PACKAGE_BEFORE = "cc/polarastrum/aiyatsbus/module/script/fluxon/core"
    const val PACKAGE_AFTER = "org/tabooproject/fluxon"

    @Synchronized
    fun createNewClass(source: String): Class<*> {
        var inputStream = AsmClassTranslation::class.java.classLoader.getResourceAsStream(source.replace('.', '/') + ".class")
        if (inputStream == null) {
            inputStream = TabooLib::class.java.classLoader.getResourceAsStream(source.replace('.', '/') + ".class")
        }
        if (inputStream == null) {
            error(
                """
                    没有找到将被转译的类 $source
                    No class found to be translated $source
                """.t()
            )
        }
        val bytes = inputStream.readBytes()
        // 转译
        val (newClass, cost2) = execution {
            val classReader = ClassReader(bytes)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
            // 创建带类名修改的 Remapper
            val renameRemapper = RelocateTranslation(PACKAGE_BEFORE, PACKAGE_AFTER, source.replace('.', '/'))
            // 转译器
            classReader.accept(ClassRemapper(classWriter, renameRemapper), 0)
            // 使用自定义 ClassLoader 定义类
            defineClass("${source}T", classWriter.toByteArray())
        }
        debug("[AsmClassTranslation] 转译 $source，用时 $cost2 毫秒。")
        return newClass
    }

    fun defineClass(name: String, bytes: ByteArray): Class<*> {
        return Bukkit.getPluginManager().getPlugin("FluxonPlugin")!!.javaClass.classLoader.invokeMethod<Class<*>>("defineClass", name, bytes, 0, bytes.size)!!
    }
}