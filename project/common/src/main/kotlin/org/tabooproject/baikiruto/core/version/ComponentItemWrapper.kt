package org.tabooproject.baikiruto.core.version

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.bukkit.inventory.ItemStack
import taboolib.library.reflex.Reflex.Companion.getProperty
import taboolib.module.nms.MinecraftVersion
import java.lang.reflect.Modifier
import java.util.Locale
import java.util.Optional
import java.util.function.Consumer

/**
 * 1.20.5+ Data Component wrapper.
 */
class ComponentItemWrapper(private val itemStack: ItemStack) {

    private val handle: Any by lazy {
        itemStack.getProperty<Any>("handle")!!
    }

    private val dataComponentTypeClass: Class<*> by lazy {
        Class.forName("net.minecraft.core.component.DataComponentType")
    }

    fun setComponent(type: Any, value: Any?) {
        if (value == null) {
            return
        }

        val attempts = ArrayList<() -> Unit>(2)
        when (value) {
            is JsonElement -> {
                attempts += { setJsonComponent(type, value) }
            }
            else -> {
                // Prefer Java input first. It avoids JsonElement classloader/shape mismatches on some runtimes.
                attempts += { setJavaComponent(type, value) }
                val jsonElement = if (value is String) {
                    parseJsonElement(value) ?: JsonPrimitive(value)
                } else {
                    toJsonElement(value)
                }
                if (jsonElement != null) {
                    attempts += { setJsonComponent(type, jsonElement) }
                }
            }
        }

        var lastError: Throwable? = null
        attempts.forEach { attempt ->
            try {
                attempt.invoke()
                return
            } catch (ex: Throwable) {
                lastError = ex
            }
        }

        if (lastError != null) {
            throw RuntimeException("Cannot parse component $type: ${formatThrowable(lastError!!)}", lastError)
        }
    }

    fun setJavaComponent(type: Any, value: Any?) {
        if (value == null) return
        val componentType = ensureDataComponentType(type)
        val codec = resolveCodec(componentType)

        try {
            val ops = getRegistryOps("JAVA")
            val result = invokeCodecParse(codec, ops, value)
            val parsed = parseDataResultValue(result)

            if (parsed != null) {
                invokeHandleSet(componentType, parsed)
            }
        } catch (e: Throwable) {
            throw RuntimeException("Cannot parse component $type: ${formatThrowable(e)}", e)
        }
    }

    fun setJsonComponent(type: Any, value: JsonElement) {
        val componentType = ensureDataComponentType(type)
        val codec = resolveCodec(componentType)

        try {
            val ops = getRegistryOps("JSON")
            val result = invokeCodecParse(codec, ops, value)
            val parsed = parseDataResultValue(result)

            if (parsed != null) {
                invokeHandleSet(componentType, parsed)
            }
        } catch (e: Throwable) {
            throw RuntimeException("Cannot parse component $type: ${formatThrowable(e)}", e)
        }
    }

    fun <T> getJavaComponent(type: Any): T? {
        val componentType = ensureDataComponentType(type)
        val codec = runCatching { resolveCodec(componentType) }.getOrNull() ?: return null

        return try {
            val componentData = invokeHandleGet(componentType) ?: return null
            val ops = getRegistryOps("JAVA")
            val result = invokeCodecEncodeStart(codec, ops, componentData)
            parseDataResultValue(result) as? T
        } catch (_: Throwable) {
            null
        }
    }

    fun removeComponent(type: Any) {
        val componentType = ensureDataComponentType(type)
        invokeHandleRemove(componentType)
    }

    fun resetComponent(type: Any) {
        val componentType = ensureDataComponentType(type)
        val item = invokeHandleGetItem() ?: return
        val componentMap = resolveComponentMap(item) ?: return
        val defaultComponent = resolveComponentMapGet(componentMap, componentType)
        invokeHandleSet(componentType, defaultComponent)
    }

    fun hasComponent(type: Any): Boolean {
        val componentType = ensureDataComponentType(type)
        return invokeHandleHas(componentType) ?: false
    }

    fun hasNonDefaultComponent(type: Any): Boolean {
        if (MinecraftVersion.versionId >= 12104) {
            val componentType = ensureDataComponentType(type)
            return invokeHandleHasNonDefault(componentType) ?: false
        }
        return hasComponent(type)
    }

    private fun ensureDataComponentType(type: Any): Any {
        if (dataComponentTypeClass.isInstance(type)) {
            return type
        }

        val key = type.toString().trim().lowercase(Locale.ENGLISH).replace('-', '_')
        val normalizedKey = if (key.startsWith("minecraft:")) key else "minecraft:$key"

        findDataComponentTypeByField(normalizedKey, dataComponentTypeClass)?.let { return it }

        val builtInRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries")
        val dataComponentTypeRegistry = builtInRegistries.getField("DATA_COMPONENT_TYPE").get(null)
        val resourceLocation = createResourceLocation(normalizedKey)

        return findRegistryValue(dataComponentTypeRegistry, resourceLocation, dataComponentTypeClass)
            ?: throw IllegalArgumentException("Unknown component type: $type")
    }

    private fun findDataComponentTypeByField(normalizedKey: String, dataComponentTypeClass: Class<*>): Any? {
        val rawPath = normalizedKey.substringAfter(':', normalizedKey).trim().takeIf { it.isNotEmpty() } ?: return null
        val fieldName = rawPath.uppercase(Locale.ENGLISH).replace('-', '_')
        val dataComponentsClass = runCatching {
            Class.forName("net.minecraft.core.component.DataComponents")
        }.getOrNull() ?: return null

        return runCatching {
            val field = dataComponentsClass.getField(fieldName)
            val value = field.get(null)
            if (value != null && dataComponentTypeClass.isInstance(value)) value else null
        }.getOrNull()
    }

    private fun findRegistryValue(registry: Any, resourceLocation: Any, dataComponentTypeClass: Class<*>): Any? {
        val methods = registry.javaClass.methods
            .asSequence()
            .filter { it.parameterCount == 1 && it.name in setOf("getValue", "get", "byName") }
            .sortedBy { method ->
                when (method.name) {
                    "getValue" -> 0
                    "get" -> 1
                    else -> 2
                }
            }
            .toList()

        methods.forEach { method ->
            if (!isRegistryKeyParameter(method.parameterTypes[0], resourceLocation)) {
                return@forEach
            }
            val raw = runCatching { method.invoke(registry, resourceLocation) }.getOrNull() ?: return@forEach
            val resolved = resolveDataComponentType(raw, dataComponentTypeClass)
            if (resolved != null) {
                return resolved
            }
        }
        return null
    }

    private fun isRegistryKeyParameter(parameterType: Class<*>, resourceLocation: Any): Boolean {
        if (parameterType.isInstance(resourceLocation)) {
            return true
        }
        val name = parameterType.name
        return name == "net.minecraft.resources.ResourceLocation" ||
            name == "net.minecraft.resources.MinecraftKey"
    }

    private fun resolveDataComponentType(raw: Any?, dataComponentTypeClass: Class<*>): Any? {
        val unwrapped = unwrapOptional(raw) ?: return null
        if (unwrapped is Number || unwrapped is Boolean || unwrapped is CharSequence) {
            return null
        }
        if (dataComponentTypeClass.isInstance(unwrapped)) {
            return unwrapped
        }
        return extractHolderValue(unwrapped, dataComponentTypeClass)
    }

    private fun extractHolderValue(holder: Any, dataComponentTypeClass: Class<*>): Any? {
        val methods = holder.javaClass.methods
            .filter { it.parameterCount == 0 && it.name in setOf("value", "getValue", "valueOrThrow") }
        methods.forEach { method ->
            val value = runCatching { method.invoke(holder) }.getOrNull() ?: return@forEach
            if (dataComponentTypeClass.isInstance(value)) {
                return value
            }
        }
        return null
    }

    private fun createResourceLocation(key: String): Any {
        val split = key.split(':', limit = 2)
        val namespace = (if (split.size == 2) split[0] else "minecraft").ifBlank { "minecraft" }
        val path = (if (split.size == 2) split[1] else split[0]).ifBlank {
            throw IllegalArgumentException("Invalid component key: $key")
        }
        val fullKey = "$namespace:$path"

        val resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation")
        tryStaticFactory(resourceLocationClass, "fromNamespaceAndPath", namespace, path)?.let { return it }
        tryStaticFactory(resourceLocationClass, "tryBuild", namespace, path)?.let { return it }
        tryStaticFactory(resourceLocationClass, "parse", fullKey)?.let { return it }
        tryStaticFactory(resourceLocationClass, "tryParse", fullKey)?.let { return it }
        if (namespace == "minecraft") {
            tryStaticFactory(resourceLocationClass, "withDefaultNamespace", path)?.let { return it }
        }
        tryConstructor(resourceLocationClass, fullKey)?.let { return it }
        tryConstructor(resourceLocationClass, namespace, path)?.let { return it }
        throw IllegalStateException("Unable to create ResourceLocation for key: $fullKey")
    }

    private fun tryStaticFactory(clazz: Class<*>, methodName: String, vararg args: Any): Any? {
        val methods = (clazz.methods.asSequence() + clazz.declaredMethods.asSequence())
            .filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    method.parameterCount == args.size
            }
            .distinctBy { method ->
                buildString {
                    append(method.name)
                    append('#')
                    append(method.parameterTypes.joinToString(",") { it.name })
                }
            }

        methods.forEach { method ->
            val value = runCatching { method.invoke(null, *args) }.getOrNull()
            val unwrapped = unwrapOptional(value)
            if (unwrapped != null) {
                return unwrapped
            }
        }
        return null
    }

    private fun tryConstructor(clazz: Class<*>, vararg args: Any): Any? {
        clazz.declaredConstructors
            .filter { it.parameterCount == args.size }
            .forEach { constructor ->
                val value = runCatching {
                    runCatching { constructor.trySetAccessible() }
                    constructor.newInstance(*args)
                }.getOrNull()
                if (value != null) {
                    return value
                }
            }
        return null
    }

    private fun getRegistryOps(type: String): Any {
        val craftServer = Class.forName("org.bukkit.Bukkit")
            .getMethod("getServer")
            .invoke(null)

        val serverHandle = resolveServerHandle(craftServer)
        val registryAccessCandidates = collectRegistryAccessCandidates(serverHandle)

        val ops = when (type) {
            "JAVA" -> {
                val javaOpsClass = Class.forName("com.mojang.serialization.JavaOps")
                javaOpsClass.getField("INSTANCE").get(null)
            }
            "JSON" -> {
                val jsonOpsClass = Class.forName("com.mojang.serialization.JsonOps")
                jsonOpsClass.getField("INSTANCE").get(null)
            }
            else -> throw IllegalArgumentException("Unknown ops type: $type")
        }

        val registryOpsClass = Class.forName("net.minecraft.resources.RegistryOps")
        registryAccessCandidates.forEach { registryAccess ->
            val createMethod = registryOpsClass.methods
                .asSequence()
                .filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 2 &&
                        isArgumentCompatible(method.parameterTypes[0], ops) &&
                        isArgumentCompatible(method.parameterTypes[1], registryAccess)
                }
                .sortedBy { method ->
                    when (method.name) {
                        "create" -> 0
                        "a" -> 1
                        else -> 2
                    }
                }
                .firstOrNull()
            if (createMethod != null) {
                return createMethod.invoke(null, ops, registryAccess)
            }
        }

        val candidateTypes = registryAccessCandidates.joinToString(", ") { it.javaClass.name }
        throw NoSuchMethodException(
            "RegistryOps#create(...) compatible overload not found; candidates=[$candidateTypes]"
        )
    }

    private fun parseDataResultValue(result: Any): Any? {
        val value = unwrapOptional(invokeNoArg(result, "result"))
        if (value != null) {
            return value
        }
        val error = unwrapOptional(invokeNoArg(result, "error"))
        if (error != null) {
            throw IllegalArgumentException(error.toString())
        }

        val getOrThrow = result.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 2 &&
                (method.parameterTypes[0] == Boolean::class.javaPrimitiveType || method.parameterTypes[0] == java.lang.Boolean::class.java) &&
                Consumer::class.java.isAssignableFrom(method.parameterTypes[1])
        }
        if (getOrThrow != null) {
            val noOpConsumer = Consumer<String> { }
            return runCatching { getOrThrow.invoke(result, false, noOpConsumer) }.getOrElse { ex ->
                throw IllegalArgumentException(formatThrowable(unwrapInvocation(ex)))
            }
        }

        throw IllegalArgumentException(result.toString())
    }

    private fun unwrapOptional(value: Any?): Any? {
        return if (value is Optional<*>) {
            value.orElse(null)
        } else {
            value
        }
    }

    private fun parseJsonElement(source: String): JsonElement? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val looksLikeJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        if (!looksLikeJson) {
            return null
        }
        return runCatching { JsonParser.parseString(trimmed) }.getOrNull()
    }

    private fun toJsonElement(value: Any?): JsonElement? {
        return when (value) {
            null -> null
            is JsonElement -> value
            is String -> parseJsonElement(value) ?: JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Char -> JsonPrimitive(value)
            is Map<*, *> -> {
                val obj = JsonObject()
                value.forEach { (rawKey, rawValue) ->
                    val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    obj.add(key, toJsonElement(rawValue))
                }
                obj
            }
            is Iterable<*> -> {
                val arr = JsonArray()
                value.forEach { entry ->
                    arr.add(toJsonElement(entry))
                }
                arr
            }
            is Array<*> -> {
                val arr = JsonArray()
                value.forEach { entry ->
                    arr.add(toJsonElement(entry))
                }
                arr
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun invokeCodecParse(codec: Any, ops: Any, value: Any): Any {
        val methods = codec.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name == "parse" &&
                    method.parameterCount == 2 &&
                    isArgumentCompatible(method.parameterTypes[0], ops) &&
                    isArgumentCompatible(method.parameterTypes[1], value)
            }
            .toList()

        var lastError: Throwable? = null
        methods.forEach { method ->
            try {
                return method.invoke(codec, ops, value)
            } catch (ex: Throwable) {
                lastError = unwrapInvocation(ex)
            }
        }
        if (lastError != null) {
            throw lastError as Throwable
        }
        throw NoSuchMethodException("Codec.parse(DynamicOps, input) not found for ${codec.javaClass.name}")
    }

    private fun invokeCodecEncodeStart(codec: Any, ops: Any, value: Any): Any {
        val methods = codec.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name == "encodeStart" &&
                    method.parameterCount == 2 &&
                    isArgumentCompatible(method.parameterTypes[0], ops) &&
                    isArgumentCompatible(method.parameterTypes[1], value)
            }
            .toList()

        var lastError: Throwable? = null
        methods.forEach { method ->
            try {
                return method.invoke(codec, ops, value)
            } catch (ex: Throwable) {
                lastError = unwrapInvocation(ex)
            }
        }
        if (lastError != null) {
            throw lastError as Throwable
        }
        throw NoSuchMethodException("Codec.encodeStart(DynamicOps, input) not found for ${codec.javaClass.name}")
    }

    private fun invokeHandleSet(componentType: Any, value: Any?) {
        val named = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name in setOf("setComponent", "set") &&
                    method.parameterCount == 2 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType) &&
                    isArgumentCompatible(method.parameterTypes[1], value)
            }
            .toList()
        val fallback = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.parameterCount == 2 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType) &&
                    isArgumentCompatible(method.parameterTypes[1], value)
            }
            .filterNot { method ->
                val second = method.parameterTypes[1].name
                second.contains("DataComponentGetter") ||
                    second.startsWith("java.util.function.")
            }
            .filter { method ->
                method !in named
            }
            .toList()
        val methods = (named + fallback)
            .toList()

        var lastError: Throwable? = null
        methods.forEach { method ->
            try {
                method.invoke(handle, componentType, value)
                return
            } catch (ex: Throwable) {
                lastError = unwrapInvocation(ex)
            }
        }
        if (lastError != null) {
            throw lastError as Throwable
        }
        throw NoSuchMethodException("No compatible ItemStack#set(...)/setComponent(...) method found")
    }

    private fun invokeHandleGet(componentType: Any): Any? {
        val methods = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name in setOf("getComponent", "get") &&
                    method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType)
            }
            .toList()

        var lastError: Throwable? = null
        methods.forEach { method ->
            try {
                return unwrapOptional(method.invoke(handle, componentType))
            } catch (ex: Throwable) {
                lastError = unwrapInvocation(ex)
            }
        }
        if (lastError != null) {
            throw lastError as Throwable
        }
        return null
    }

    private fun invokeHandleRemove(componentType: Any) {
        val named = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name in setOf("removeComponent", "remove") &&
                    method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType)
            }
            .toList()
        val fallback = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType) &&
                    method.returnType != java.lang.Boolean.TYPE &&
                    method.returnType != java.lang.Boolean::class.java
            }
            .filter { method ->
                method !in named
            }
            .toList()
        val methods = (named + fallback)
            .toList()

        var lastError: Throwable? = null
        methods.forEach { method ->
            try {
                method.invoke(handle, componentType)
                return
            } catch (ex: Throwable) {
                lastError = unwrapInvocation(ex)
            }
        }
        if (lastError != null) {
            throw lastError as Throwable
        }
        throw NoSuchMethodException("No compatible ItemStack#remove(...)/removeComponent(...) method found")
    }

    private fun invokeHandleHas(componentType: Any): Boolean? {
        val named = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name in setOf("hasComponent", "has") &&
                    method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType)
            }
            .toList()
        val fallback = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType) &&
                    (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java)
            }
            .filter { method ->
                method !in named
            }
            .toList()
        val methods = (named + fallback)
            .toList()
        methods.forEach { method ->
            val value = runCatching { method.invoke(handle, componentType) }.getOrNull() as? Boolean
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun invokeHandleHasNonDefault(componentType: Any): Boolean? {
        val named = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name in setOf("hasNonDefaultComponent", "hasNonDefault") &&
                    method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType)
            }
            .toList()
        val fallback = handle.javaClass.methods
            .asSequence()
            .filter { method ->
                method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType) &&
                    (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java)
            }
            .filter { method ->
                method !in named
            }
            .toList()
        val methods = (named + fallback)
            .toList()
        methods.forEach { method ->
            val value = runCatching { method.invoke(handle, componentType) }.getOrNull() as? Boolean
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun invokeHandleGetItem(): Any? {
        return runCatching {
            handle.javaClass.methods.firstOrNull { method ->
                method.name == "getItem" && method.parameterCount == 0
            }?.invoke(handle)
        }.getOrNull()
    }

    private fun resolveComponentMap(item: Any): Any? {
        val methods = item.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name in setOf("components", "getComponents", "getPrototype", "immutableComponents") &&
                    method.parameterCount == 0
            }
            .toList()
        methods.forEach { method ->
            val value = runCatching { method.invoke(item) }.getOrNull()
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun resolveComponentMapGet(componentMap: Any, componentType: Any): Any? {
        val methods = componentMap.javaClass.methods
            .asSequence()
            .filter { method ->
                method.name == "get" &&
                    method.parameterCount == 1 &&
                    isArgumentCompatible(method.parameterTypes[0], componentType)
            }
            .toList()

        methods.forEach { method ->
            val value = runCatching { method.invoke(componentMap, componentType) }.getOrNull()
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun isArgumentCompatible(parameterType: Class<*>, value: Any?): Boolean {
        if (value == null) {
            return !parameterType.isPrimitive
        }
        if (parameterType.isInstance(value)) {
            return true
        }
        if (parameterType == Any::class.java || parameterType == Any::class.javaObjectType) {
            return true
        }
        if (parameterType.isPrimitive) {
            val wrapper = when (parameterType) {
                java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
                java.lang.Byte.TYPE -> java.lang.Byte::class.java
                java.lang.Short.TYPE -> java.lang.Short::class.java
                java.lang.Integer.TYPE -> java.lang.Integer::class.java
                java.lang.Long.TYPE -> java.lang.Long::class.java
                java.lang.Float.TYPE -> java.lang.Float::class.java
                java.lang.Double.TYPE -> java.lang.Double::class.java
                java.lang.Character.TYPE -> java.lang.Character::class.java
                else -> null
            }
            if (wrapper != null && wrapper.isInstance(value)) {
                return true
            }
        }
        return parameterType.isAssignableFrom(value.javaClass)
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        val methods = target.javaClass.methods
            .asSequence()
            .filter { method -> method.name == methodName && method.parameterCount == 0 }
            .toList()
        methods.forEach { method ->
            val value = runCatching { method.invoke(target) }.getOrNull()
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun resolveCodec(componentType: Any): Any {
        val codecClass = Class.forName("com.mojang.serialization.Codec")
        val preferredNames = setOf("codec", "codecOrThrow", "b", "c")

        val methods = (componentType.javaClass.methods.asSequence() + componentType.javaClass.declaredMethods.asSequence())
            .filter { method ->
                method.parameterCount == 0 &&
                    (codecClass.isAssignableFrom(method.returnType) || method.name in preferredNames)
            }
            .distinctBy { method ->
                buildString {
                    append(method.name)
                    append('#')
                    append(method.returnType.name)
                }
            }
            .sortedBy { method ->
                when (method.name) {
                    "codec" -> 0
                    "codecOrThrow" -> 1
                    "b" -> 2
                    "c" -> 3
                    else -> 4
                }
            }
            .toList()

        methods.forEach { method ->
            val value = runCatching {
                runCatching { method.trySetAccessible() }
                method.invoke(componentType)
            }.getOrNull()
            val codec = unwrapOptional(value)
            if (codec != null && codecClass.isInstance(codec)) {
                return codec
            }
        }
        throw NoSuchMethodException("DataComponentType codec accessor not found on ${componentType.javaClass.name}")
    }

    private fun resolveServerHandle(craftServer: Any): Any {
        val directCandidates = linkedSetOf<Any>()
        invokeNoArg(craftServer, "getServer")?.let(directCandidates::add)
        invokeNoArg(craftServer, "getHandle")?.let(directCandidates::add)
        directCandidates.add(craftServer)

        directCandidates.forEach { candidate ->
            if (hasLikelyRegistryAccessMethod(candidate)) {
                return candidate
            }
            val nested = listOf(
                invokeNoArg(candidate, "getServer"),
                invokeNoArg(candidate, "getMinecraftServer"),
                invokeNoArg(candidate, "c"),
                invokeNoArg(candidate, "b")
            ).firstOrNull { it != null }
            if (nested != null && hasLikelyRegistryAccessMethod(nested)) {
                return nested
            }
        }

        return directCandidates.first()
    }

    private fun hasLikelyRegistryAccessMethod(target: Any): Boolean {
        return target.javaClass.methods.any { method ->
            method.parameterCount == 0 &&
                method.name in setOf("registryAccess", "bg", "registries", "bh")
        }
    }

    private fun collectRegistryAccessCandidates(serverHandle: Any): List<Any> {
        val queue = ArrayDeque<Pair<Any, Int>>()
        val visited = linkedSetOf<String>()
        val result = linkedSetOf<Any>()
        queue += serverHandle to 0

        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            val id = "${current.javaClass.name}@${System.identityHashCode(current)}"
            if (!visited.add(id) || depth > 3) {
                continue
            }

            val namedRegistryMethods = listOf("registryAccess", "bg", "registries", "bh")
            namedRegistryMethods.forEach { methodName ->
                invokeNoArg(current, methodName)?.let(result::add)
            }

            current.javaClass.methods
                .asSequence()
                .filter { method ->
                    method.parameterCount == 0 &&
                        (method.returnType.name.contains("IRegistryCustom") ||
                            method.returnType.name.contains("LayeredRegistryAccess") ||
                            method.returnType.name.contains("HolderLookup"))
                }
                .forEach { method ->
                    val value = runCatching { method.invoke(current) }.getOrNull()
                    if (value != null) {
                        result += value
                    }
                }

            val nestedServerMethods = listOf("getServer", "getMinecraftServer", "server", "c", "b")
            nestedServerMethods.forEach { methodName ->
                val nested = invokeNoArg(current, methodName) ?: return@forEach
                if (nested !== current) {
                    queue += nested to (depth + 1)
                }
            }
        }

        if (result.isEmpty()) {
            result += serverHandle
        }
        return result.toList()
    }

    private fun unwrapInvocation(ex: Throwable): Throwable {
        return if (ex is java.lang.reflect.InvocationTargetException) {
            ex.targetException ?: ex
        } else {
            ex
        }
    }

    private fun formatThrowable(ex: Throwable): String {
        val chain = mutableListOf<String>()
        var cursor: Throwable? = ex
        while (cursor != null && chain.size < 4) {
            val message = cursor.message?.trim().takeIf { !it.isNullOrEmpty() } ?: cursor.javaClass.name
            if (chain.isEmpty() || chain.last() != message) {
                chain += message
            }
            cursor = cursor.cause
        }
        return chain.joinToString(" <- ")
    }

    fun getItemStack(): ItemStack = itemStack
}
