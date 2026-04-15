package org.tabooproject.baikiruto.impl.script.relocate

import org.bukkit.Bukkit
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import taboolib.common.TabooLib
import taboolib.common.platform.function.debug
import taboolib.common.util.execution
import taboolib.common.util.t

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.relocate.AsmClassTranslation
 *
 * @author mical
 * @since 2026/1/3 14:05
 */
object AsmClassTranslation {

    const val PACKAGE_BEFORE = "org/tabooproject/baikiruto/impl/script/fluxon"
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
        val (newClass, cost2) = execution {
            val classReader = ClassReader(bytes)
            val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
            val renameRemapper = RelocateTranslation(PACKAGE_BEFORE, PACKAGE_AFTER, source.replace('.', '/'))
            classReader.accept(ClassRemapper(classWriter, renameRemapper), 0)
            defineClass("${source}T", classWriter.toByteArray())
        }
        debug("[AsmClassTranslation] 转译 $source，用时 $cost2 毫秒。")
        return newClass
    }

    fun defineClass(name: String, bytes: ByteArray): Class<*> {
        val fluxonPlugin = Bukkit.getPluginManager().getPlugin("FluxonPlugin")
            ?: error("FluxonPlugin 未加载，无法定义转译类：$name")
        check(fluxonPlugin.isEnabled) { "FluxonPlugin 尚未启用，无法定义转译类：$name" }
        val classLoader = fluxonPlugin.javaClass.classLoader
        val defineClass = classLoader.javaClass.methods.firstOrNull { method ->
            method.name == "defineClass" &&
                method.parameterCount == 4 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == ByteArray::class.java &&
                method.parameterTypes[2] == Int::class.javaPrimitiveType &&
                method.parameterTypes[3] == Int::class.javaPrimitiveType
        } ?: error("FluxonPlugin ClassLoader 不支持 defineClass(String, ByteArray, Int, Int)")
        return try {
            defineClass.invoke(classLoader, name, bytes, 0, bytes.size) as? Class<*>
                ?: error("FluxonPlugin ClassLoader#defineClass 返回空结果：$name")
        } catch (ex: ReflectiveOperationException) {
            throw IllegalStateException("调用 FluxonPlugin ClassLoader#defineClass 失败：$name", ex)
        }
    }
}
