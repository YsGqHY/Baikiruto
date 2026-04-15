package org.tabooproject.baikiruto.impl.hook

internal object SkullProfileBridge {

    fun copy(sourceMeta: Any, targetMeta: Any): Boolean {
        return copyViaAccessors(sourceMeta, targetMeta) || copyFields(sourceMeta, targetMeta)
    }

    private fun copyViaAccessors(sourceMeta: Any, targetMeta: Any): Boolean {
        val getters = sourceMeta.javaClass.methods.filter { method ->
            method.parameterCount == 0 && method.name in PROFILE_GETTER_NAMES
        }
        getters.forEach { getter ->
            val profile = reflectOrNull { getter.invoke(sourceMeta) } ?: return@forEach
            val setter = targetMeta.javaClass.methods.firstOrNull { method ->
                method.parameterCount == 1 &&
                    method.name in PROFILE_SETTER_NAMES &&
                    method.parameterTypes[0].isAssignableFrom(profile.javaClass)
            } ?: return@forEach
            if (reflectSucceeded { setter.invoke(targetMeta, profile) }) {
                return true
            }
        }
        return false
    }

    private fun copyFields(sourceMeta: Any, targetMeta: Any): Boolean {
        var copied = false
        collectProfileFields(sourceMeta.javaClass).forEach { sourceField ->
            val targetField = collectProfileFields(targetMeta.javaClass).firstOrNull { candidate ->
                candidate.name == sourceField.name && candidate.type.isAssignableFrom(sourceField.type)
            } ?: return@forEach
            val value = reflectOrNull {
                sourceField.isAccessible = true
                sourceField.get(sourceMeta)
            } ?: return@forEach
            if (reflectSucceeded {
                    targetField.isAccessible = true
                    targetField.set(targetMeta, value)
                }
            ) {
                copied = true
            }
        }
        return copied
    }

    private fun <T> reflectOrNull(block: () -> T): T? {
        return try {
            block()
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun reflectSucceeded(block: () -> Unit): Boolean {
        return reflectOrNull {
            block()
            true
        } == true
    }

    private fun collectProfileFields(type: Class<*>): List<java.lang.reflect.Field> {
        val fields = ArrayList<java.lang.reflect.Field>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            current.declaredFields
                .filter { field ->
                    field.name.contains("profile", ignoreCase = true) ||
                        field.name == "ownerProfile" ||
                        field.name == "playerProfile"
                }
                .forEach(fields::add)
            current = current.superclass
        }
        return fields.distinctBy { field -> field.name }
    }

    private val PROFILE_GETTER_NAMES = setOf("getPlayerProfile", "getOwnerProfile", "getProfile")
    private val PROFILE_SETTER_NAMES = setOf("setPlayerProfile", "setOwnerProfile", "setProfile")
}
