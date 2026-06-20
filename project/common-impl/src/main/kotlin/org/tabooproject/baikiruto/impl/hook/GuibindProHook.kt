package org.tabooproject.baikiruto.impl.hook

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.item.ItemUpdateStatePreserver
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID

object GuibindProHook : ItemUpdateStatePreserver {

    private val PLUGIN_NAMES = arrayOf("GuibindPro", "GuiBindPro")

    override fun preserve(source: ItemStack, rebuilt: ItemStack, player: Player?): ItemStack {
        if (!BaikirutoSettings.guibindProHookEnabled) {
            return rebuilt
        }
        return try {
            val bridge = resolveBridge() ?: return rebuilt
            val snapshot = bridge.snapshot(source, player) ?: return rebuilt
            snapshot.restore(rebuilt, player, bridge)
        } catch (_: Throwable) {
            rebuilt
        }
    }

    private fun BindingSnapshot.restore(rebuilt: ItemStack, player: Player?, bridge: Bridge): ItemStack {
        var current = rebuilt
        if (bound) {
            current = restoreBound(current, player, bridge)
        } else if (unbound) {
            current = bridge.setUnbound(current) ?: current
        }
        if (!recoverySignature.isNullOrBlank()) {
            current = bridge.setRecoverySignature(current, recoverySignature) ?: current
        }
        return current
    }

    private fun BindingSnapshot.restoreBound(rebuilt: ItemStack, player: Player?, bridge: Bridge): ItemStack {
        val ownerPlayer = resolveOwnerPlayer(player, bridge)
        if (ownerPlayer != null) {
            val fullRestore = bridge.setBindAll(rebuilt, ownerPlayer)
                ?: bridge.setBind(rebuilt, ownerPlayer)
            if (fullRestore != null) {
                val withNbt = bridge.setBindNbt(fullRestore, ownerPlayer) ?: fullRestore
                return restoreBoundLore(withNbt, ownerPlayer.name, bridge)
            }
            val nbtOnly = bridge.setBindNbt(rebuilt, ownerPlayer)
            if (nbtOnly != null) {
                return restoreBoundLore(nbtOnly, ownerPlayer.name, bridge)
            }
        }
        return restoreBoundLore(rebuilt, ownerName, bridge)
    }

    internal fun restoreBoundLoreForTesting(itemStack: ItemStack, boundLoreLine: String, bindLoreIndex: Int = -1): ItemStack {
        return transplantBoundLore(itemStack, boundLoreLine, TestBridge(bindLoreIndex))
    }

    private fun BindingSnapshot.restoreBoundLore(itemStack: ItemStack, ownerName: String?, bridge: BindLoreBridge): ItemStack {
        val sourceLoreLine = boundLoreLine?.takeIf { it.isNotBlank() }
        if (bridge.hasBindLore(itemStack)) {
            return if (sourceLoreLine != null) {
                transplantBoundLore(itemStack, sourceLoreLine, bridge)
            } else {
                itemStack
            }
        }
        val generated = ownerName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { owner -> bridge.setBindLore(itemStack, owner) }
        if (generated != null) {
            return if (sourceLoreLine != null && !bridge.hasBindLore(generated)) {
                transplantBoundLore(generated, sourceLoreLine, bridge)
            } else {
                generated
            }
        }
        return if (sourceLoreLine != null) {
            transplantBoundLore(itemStack, sourceLoreLine, bridge)
        } else {
            itemStack
        }
    }

    private fun BindingSnapshot.resolveOwnerPlayer(player: Player?, bridge: Bridge): Player? {
        if (player != null) {
            val playerName = player.name.trim()
            val playerUuid = player.uniqueId
            if (currentPlayerOwns || ownerName.equals(playerName, ignoreCase = true) || ownerUuid == playerUuid) {
                return player
            }
        }
        val resolvedName = ownerName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: ownerUuid?.let(::offlineName)
        val resolvedUuid = ownerUuid
            ?: resolvedName?.let(::offlineUuid)
        if (resolvedName.isNullOrBlank() && resolvedUuid == null) {
            return null
        }
        return createPlayerProxy(
            name = resolvedName ?: resolvedUuid.toString(),
            uuid = resolvedUuid ?: UUID.nameUUIDFromBytes("OfflinePlayer:$resolvedName".toByteArray(Charsets.UTF_8)),
            server = bridge.server
        )
    }

    private fun transplantBoundLore(itemStack: ItemStack, boundLoreLine: String, bridge: BindLoreBridge): ItemStack {
        val itemMeta = itemStack.itemMeta ?: return itemStack
        val lore = itemMeta.lore?.toMutableList() ?: mutableListOf()
        val strippedTarget = stripColor(boundLoreLine)

        // 检查是否已存在相同内容的绑定行（去色比较）
        val duplicateIndex = lore.indexOfFirst { stripColor(it) == strippedTarget }
        if (duplicateIndex >= 0) {
            // 已存在，替换为带颜色的版本
            lore[duplicateIndex] = boundLoreLine
            itemMeta.lore = lore
            itemStack.itemMeta = itemMeta
            return itemStack
        }

        // 查找 GuibindPro 预期的绑定行位置
        val bindLoreIndex = bridge.findBindLoreIndex(lore)
        if (bindLoreIndex in lore.indices) {
            // 找到预期位置，替换该行
            lore[bindLoreIndex] = boundLoreLine
        } else {
            // 未找到预期位置，插入到开头（GuibindPro 默认行为）
            lore.add(0, boundLoreLine)
        }

        itemMeta.lore = lore
        itemStack.itemMeta = itemMeta
        return itemStack
    }

    private fun resolveBridge(): Bridge? {
        val pluginManager = try {
            Bukkit.getPluginManager()
        } catch (_: Throwable) {
            null
        } ?: return null
        val plugin = PLUGIN_NAMES.asSequence()
            .mapNotNull { name ->
                try {
                    pluginManager.getPlugin(name)
                } catch (_: Throwable) {
                    null
                }
            }
            .firstOrNull()
            ?: return null
        if (!plugin.isEnabled) {
            return null
        }
        val pluginClass = plugin.javaClass
        val main = staticField(pluginClass, "main") ?: plugin
        return Bridge(
            server = try { Bukkit.getServer() } catch (_: Throwable) { null },
            api = staticField(pluginClass, "api"),
            bindingManager = call(main, "getBindingManager") ?: field(main, "bindingManager"),
            loreBindUtil = call(main, "getLoreBindUtil") ?: field(main, "loreBindUtil")
        )
    }

    private data class BindingSnapshot(
        val bound: Boolean,
        val unbound: Boolean,
        val ownerName: String?,
        val ownerUuid: UUID?,
        val currentPlayerOwns: Boolean,
        val boundLoreLine: String?,
        val recoverySignature: String?
    )

    private interface BindLoreBridge {

        fun hasBindLore(itemStack: ItemStack): Boolean

        fun setBindLore(itemStack: ItemStack, ownerName: String): ItemStack?

        fun findBindLoreIndex(lore: List<String>): Int
    }

    private class TestBridge(private val bindLoreIndex: Int) : BindLoreBridge {

        override fun hasBindLore(itemStack: ItemStack): Boolean {
            val lore = itemStack.itemMeta?.lore ?: return false
            return findBindLoreIndex(lore) in lore.indices
        }

        override fun setBindLore(itemStack: ItemStack, ownerName: String): ItemStack? {
            return null
        }

        override fun findBindLoreIndex(lore: List<String>): Int {
            return bindLoreIndex
        }
    }

    private class Bridge(
        val server: Any?,
        private val api: Any?,
        private val bindingManager: Any?,
        private val loreBindUtil: Any?
    ) : BindLoreBridge {

        fun snapshot(source: ItemStack, player: Player?): BindingSnapshot? {
            val bound = hasBind(source)
            val unbound = hasUnbound(source)
            val recoverySignature = stringCall(api, "getRecoverySignature", source)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (!bound && !unbound && recoverySignature == null) {
                return null
            }
            val owner = owner(source)
            val loreOwner = stringCall(loreBindUtil, "getOwner", source)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val currentPlayerOwns = player != null && hasBindUser(source, player)
            val ownerUuid = uuidCall(api, "getBindUUID", source)
                ?: uuidCall(bindingManager, "getNbtUUID", source)
                ?: owner?.uniqueId
                ?: loreOwner?.let(::offlineUuid)
            val ownerName = loreOwner
                ?: owner?.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: player?.name?.trim()?.takeIf { currentPlayerOwns && it.isNotEmpty() }
                ?: ownerUuid?.let(::offlineName)
            return BindingSnapshot(
                bound = bound,
                unbound = unbound,
                ownerName = ownerName,
                ownerUuid = ownerUuid,
                currentPlayerOwns = currentPlayerOwns,
                boundLoreLine = if (bound) findBoundLoreLine(source, ownerName) else null,
                recoverySignature = recoverySignature
            )
        }

        fun hasBind(itemStack: ItemStack): Boolean {
            return booleanCall(api, "hasBind", itemStack)
                ?: booleanCall(bindingManager, "hasBind", itemStack)
                ?: false
        }

        override fun hasBindLore(itemStack: ItemStack): Boolean {
            val lore = itemStack.itemMeta?.lore ?: return false
            return findBindLoreIndex(lore) in lore.indices
        }

        fun hasUnbound(itemStack: ItemStack): Boolean {
            return booleanCall(api, "hasUnbound", itemStack)
                ?: booleanCall(bindingManager, "hasUnbound", itemStack)
                ?: false
        }

        fun hasBindUser(itemStack: ItemStack, player: Player): Boolean {
            return booleanCall(api, "hasBindUser", itemStack, player)
                ?: booleanCall(bindingManager, "hasUserBind", itemStack, player)
                ?: false
        }

        fun setBindAll(itemStack: ItemStack, player: Player): ItemStack? {
            return itemCall(bindingManager, "setBindAll", itemStack, player)
        }

        fun setBind(itemStack: ItemStack, player: Player): ItemStack? {
            return itemCall(api, "setBind", itemStack, player)
                ?: itemCall(bindingManager, "setBind", itemStack, player)
        }

        override fun setBindLore(itemStack: ItemStack, ownerName: String): ItemStack? {
            return itemCall(loreBindUtil, "bind", itemStack, ownerName)
        }

        fun setBindNbt(itemStack: ItemStack, player: Player): ItemStack? {
            return itemCall(bindingManager, "setBindNbt", itemStack, player)
        }

        fun setUnbound(itemStack: ItemStack): ItemStack? {
            return itemCall(api, "setUnbound", itemStack)
                ?: itemCall(bindingManager, "setUnbound", itemStack)
        }

        fun setRecoverySignature(itemStack: ItemStack, signature: String): ItemStack? {
            return itemCall(api, "setRecoverySignature", itemStack, signature)
        }

        override fun findBindLoreIndex(lore: List<String>): Int {
            return intCall(loreBindUtil, "findBindLoreIndex", lore) ?: -1
        }

        private fun owner(itemStack: ItemStack): OfflinePlayer? {
            return call(api, "getItemOwner", itemStack) as? OfflinePlayer
                ?: call(bindingManager, "getItemOwner", itemStack) as? OfflinePlayer
        }

        private fun findBoundLoreLine(itemStack: ItemStack, ownerName: String?): String? {
            val lore = itemStack.itemMeta?.lore ?: return null
            val index = findBindLoreIndex(lore)
            if (index in lore.indices) {
                return lore[index]
            }
            val plainOwner = ownerName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return lore.firstOrNull { stripColor(it).contains(plainOwner, ignoreCase = true) }
        }
    }

    private fun createPlayerProxy(name: String, uuid: UUID, server: Any?): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "getName" -> name
                "getUniqueId" -> uuid
                "isOp" -> false
                "getServer" -> server
                "hasPermission" -> false
                "sendMessage" -> null
                "toString" -> "BaikirutoGuibindProOwner(name=$name, uuid=$uuid)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultReturnValue(method)
            }
        } as Player
    }

    private fun defaultReturnValue(method: Method): Any? {
        return when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Void.TYPE -> null
            else -> null
        }
    }

    private fun staticField(type: Class<*>, name: String): Any? {
        return try {
            type.getField(name).get(null)
        } catch (_: Throwable) {
            null
        }
    }

    private fun field(target: Any?, name: String): Any? {
        if (target == null) {
            return null
        }
        return try {
            val field = target.javaClass.fields.firstOrNull { it.name == name }
                ?: target.javaClass.getDeclaredField(name).also { it.isAccessible = true }
            field.get(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun call(target: Any?, name: String, vararg args: Any?): Any? {
        if (target == null) {
            return null
        }
        return try {
            val method = target.javaClass.methods.firstOrNull { method ->
                method.name == name && method.parameterCount == args.size && parametersMatch(method.parameterTypes, args)
            } ?: return null
            method.invoke(target, *args)
        } catch (_: Throwable) {
            null
        }
    }

    private fun parametersMatch(types: Array<Class<*>>, args: Array<out Any?>): Boolean {
        return types.indices.all { index ->
            val arg = args[index] ?: return@all true
            box(types[index]).isInstance(arg)
        }
    }

    private fun box(type: Class<*>): Class<*> {
        if (!type.isPrimitive) {
            return type
        }
        return when (type) {
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            java.lang.Void.TYPE -> java.lang.Void::class.java
            else -> type
        }
    }

    private fun booleanCall(target: Any?, name: String, vararg args: Any?): Boolean? {
        return call(target, name, *args) as? Boolean
    }

    private fun intCall(target: Any?, name: String, vararg args: Any?): Int? {
        return (call(target, name, *args) as? Number)?.toInt()
    }

    private fun stringCall(target: Any?, name: String, vararg args: Any?): String? {
        return call(target, name, *args) as? String
    }

    private fun uuidCall(target: Any?, name: String, vararg args: Any?): UUID? {
        return call(target, name, *args) as? UUID
    }

    private fun itemCall(target: Any?, name: String, vararg args: Any?): ItemStack? {
        return call(target, name, *args) as? ItemStack
    }

    private fun offlineName(uuid: UUID): String? {
        return try {
            Bukkit.getOfflinePlayer(uuid).name?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun offlineUuid(name: String): UUID? {
        return try {
            Bukkit.getOfflinePlayer(name).uniqueId
        } catch (_: Throwable) {
            null
        }
    }

    private fun stripColor(value: String): String {
        return ChatColor.stripColor(value).orEmpty().trim()
    }
}
