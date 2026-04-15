package org.tabooproject.baikiruto.core

import taboolib.library.reflex.LazyClass

object ClassAccess {

    fun isAvailable(name: String, classLoader: ClassLoader = ClassAccess::class.java.classLoader): Boolean {
        return load(name, initialize = false, classLoader = classLoader) != null
    }

    fun load(
        name: String,
        initialize: Boolean = false,
        classLoader: ClassLoader = ClassAccess::class.java.classLoader
    ): Class<*>? {
        return try {
            Class.forName(name, initialize, classLoader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: NoClassDefFoundError) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    fun resolveLazy(name: String): Class<*>? {
        return try {
            LazyClass.of(source = name, dimensions = 0, isPrimitive = false, classFinder = null).instance
        } catch (_: IllegalStateException) {
            null
        } catch (_: TypeNotPresentException) {
            null
        } catch (_: NoClassDefFoundError) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}
