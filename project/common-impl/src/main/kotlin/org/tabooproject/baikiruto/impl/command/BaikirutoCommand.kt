package org.tabooproject.baikiruto.impl.command

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import org.tabooproject.baikiruto.impl.menu.BaikirutoGroupMenu
import org.tabooproject.baikiruto.impl.ops.BaikirutoDiagnostics
import org.tabooproject.baikiruto.impl.ops.BaikirutoReloader
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.command.suggestUncheck
import taboolib.expansion.createHelper
import taboolib.platform.util.isAir
import taboolib.platform.util.sendLang

@CommandHeader(
    name = "baikiruto",
    aliases = ["bkr", "bai"],
    permission = "baikiruto.command"
)
object BaikirutoCommand {

    private const val DEFAULT_ITEM_ID = "example:all_features"

    @CommandBody(permission = "baikiruto.command")
    val main = mainCommand {
        createHelper()
    }

    @CommandBody(permission = "baikiruto.command.reload")
    val reload = subCommand {
        literal("items") {
            exec<CommandSender> {
                val r = BaikirutoReloader.reloadItems()
                if (r.onlineUpdateEnabled) {
                    sender.sendLang("log-reload-items", r.items, r.updatedPlayers)
                } else {
                    sender.sendLang("log-reload-items-no-update", r.items)
                }
            }
        }
        literal("scripts") {
            exec<CommandSender> {
                val r = BaikirutoReloader.reloadScripts()
                sender.sendLang("log-reload-scripts", r.cacheSize, r.totalCompilations)
            }
        }
        literal("debug") {
            exec<CommandSender> {
                sender.sendLang("command-reload-debug-header")
                // Diagnostics 输出为 key=value 技术格式，有意不国际化
                BaikirutoDiagnostics.lines().forEach(sender::sendMessage)
            }
        }
        exec<CommandSender> {
            val r = BaikirutoReloader.reloadAll()
            if (r.onlineUpdateEnabled) {
                sender.sendLang("log-reload-all", r.items, r.updatedPlayers, r.costMs)
            } else {
                sender.sendLang("log-reload-all-no-update", r.items, r.costMs)
            }
        }
    }

    @CommandBody(permission = "baikiruto.command.list")
    val list = subCommand {
        dynamic("keyword") {
            exec<CommandSender> {
                executeList(sender, ctx["keyword"])
            }
        }
        exec<CommandSender> {
            executeList(sender, null)
        }
    }

    @CommandBody(permission = "baikiruto.command.give")
    val give = subCommand {
        dynamic("itemId") {
            suggestUncheck {
                Baikiruto.api().getItemRegistry().keys().sorted()
            }
            dynamic("targetOrAmount") {
                suggestUncheck {
                    Bukkit.getOnlinePlayers().map { it.name }.sorted() + listOf("1", "16", "64")
                }
                dynamic("amount") {
                    exec<CommandSender> {
                        executeGiveFromCommand(sender, ctx["itemId"], ctx["targetOrAmount"], ctx["amount"])
                    }
                }
                exec<CommandSender> {
                    executeGiveFromCommand(sender, ctx["itemId"], ctx["targetOrAmount"], null)
                }
            }
            exec<CommandSender> {
                executeGiveSelf(sender, ctx["itemId"], 1)
            }
        }
    }

    @CommandBody(permission = "baikiruto.command.serialize")
    val serialize = subCommand {
        exec<CommandSender> {
            executeSerialize(sender)
        }
    }

    @CommandBody(permission = "baikiruto.command.rebuild")
    val rebuild = subCommand {
        exec<CommandSender> {
            executeRebuild(sender)
        }
    }

    @CommandBody(permission = "baikiruto.command.menu")
    val menu = subCommand {
        dynamic("groupId") {
            suggestUncheck {
                Baikiruto.api().getGroupRegistry().keys().sorted()
            }
            exec<CommandSender> {
                executeMenu(sender, ctx["groupId"])
            }
        }
        exec<CommandSender> {
            executeMenu(sender, null)
        }
    }

    @CommandBody(permission = "baikiruto.command.debug")
    val debug = subCommand {
        literal("build") {
            dynamic("itemId") {
                suggestUncheck {
                    Baikiruto.api().getItemRegistry().keys().sorted()
                }
                exec<CommandSender> {
                    val itemId = ctx["itemId"]
                    executeBuild(sender, itemId)
                }
            }
            exec<CommandSender> {
                executeBuild(sender, DEFAULT_ITEM_ID)
            }
        }
        literal("give") {
            dynamic("itemId") {
                suggestUncheck {
                    Baikiruto.api().getItemRegistry().keys().sorted()
                }
                dynamic("amount") {
                    exec<CommandSender> {
                        executeGiveSelf(sender, ctx["itemId"], ctx["amount"].toIntOrNull() ?: 1)
                    }
                }
                exec<CommandSender> {
                    executeGiveSelf(sender, ctx["itemId"], 1)
                }
            }
            exec<CommandSender> {
                executeGiveSelf(sender, DEFAULT_ITEM_ID, 1)
            }
        }
        literal("metrics") {
            exec<CommandSender> {
                BaikirutoDiagnostics.lines().forEach(sender::sendMessage)
            }
        }
        literal("read") {
            exec<CommandSender> {
                executeRead(sender)
            }
        }
        literal("update") {
            exec<CommandSender> {
                executeUpdate(sender)
            }
        }
    }

    @CommandBody(permission = "baikiruto.command.debug")
    val selfcheck = subCommand {
        exec<CommandSender> {
            sender.sendLang("command-selfcheck-header")
            BaikirutoDiagnostics.lines().forEach(sender::sendMessage)
            sender.sendLang("command-selfcheck-items", ItemDefinitionLoader.loadedIds().joinToString(", "))
        }
    }

    private fun executeBuild(sender: CommandSender, itemId: String) {
        val player = sender as? Player
        val result = Baikiruto.api().buildItem(
            itemId = itemId,
            context = linkedMapOf<String, Any?>(
                "debug" to true,
                "senderName" to sender.name,
                "sender" to sender,
                "player" to player
            )
        )
        if (result == null) {
            sender.sendLang("command-build-failed", itemId)
            return
        }
        sender.sendLang("command-build-success", itemId, result.type, result.amount)
    }

    private fun executeList(sender: CommandSender, keyword: String?) {
        val allItems = Baikiruto.api().getItemRegistry().keys().sorted()
        if (allItems.isEmpty()) {
            sender.sendLang("command-list-empty")
            return
        }
        val normalizedKeyword = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val filtered = if (normalizedKeyword == null) {
            allItems
        } else {
            allItems.filter { id -> id.contains(normalizedKeyword, ignoreCase = true) }
        }
        if (filtered.isEmpty()) {
            sender.sendLang("command-list-no-match", normalizedKeyword.orEmpty())
            return
        }
        sender.sendLang("command-list-header", filtered.size, allItems.size)
        filtered.forEach { id ->
            sender.sendLang("command-list-entry", id)
        }
    }

    private fun executeRead(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendLang("command-read-player-only")
            return
        }
        val stream = Baikiruto.api().readItem(player.inventory.itemInMainHand)
        if (stream == null) {
            sender.sendLang("command-read-not-managed")
            return
        }
        sender.sendLang("command-read-header")
        sender.sendLang("command-read-id", stream.itemId)
        sender.sendLang("command-read-version", stream.versionHash)
        sender.sendLang("command-read-metas", if (stream.metaHistory.isEmpty()) "[]" else stream.metaHistory.joinToString(", ", "[", "]"))
        sender.sendLang("command-read-signals", if (stream.signals.isEmpty()) "[]" else stream.signals.joinToString(", ", "[", "]"))
        if (stream.runtimeData.isEmpty()) {
            sender.sendLang("command-read-runtime-empty")
            return
        }
        sender.sendLang("command-read-runtime-header")
        stream.runtimeData.entries.sortedBy { it.key }.forEach { (key, value) ->
            sender.sendLang("command-read-runtime-entry", key, value.toString())
        }
    }

    private fun executeUpdate(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendLang("command-update-player-only")
            return
        }
        val updated = Baikiruto.api().getItemUpdater().checkUpdate(player, player.inventory)
        sender.sendLang("command-update-complete", updated)
    }

    private fun executeSerialize(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendLang("command-serialize-player-only")
            return
        }
        val item = player.inventory.itemInMainHand
        if (item.isAir()) {
            sender.sendLang("command-serialize-empty-hand")
            return
        }
        val serialized = Baikiruto.api().getItemSerializer().serialize(item)
        sender.sendLang("command-serialize-header")
        sender.sendLang("command-serialize-item-id", serialized.itemId)
        sender.sendLang("command-serialize-amount", serialized.amount)
        sender.sendLang("command-serialize-version", serialized.versionHash)
        sender.sendLang("command-serialize-metas", if (serialized.metaHistory.isEmpty()) "[]" else serialized.metaHistory.joinToString(", ", "[", "]"))
        if (serialized.runtimeData.isEmpty()) {
            sender.sendLang("command-serialize-runtime-empty")
        } else {
            sender.sendLang("command-serialize-runtime-header")
            serialized.runtimeData.entries.sortedBy { it.key }.forEach { (key, value) ->
                sender.sendLang("command-serialize-runtime-entry", key, value.toString())
            }
        }
        val encoded = serialized.itemStackData
        val preview = if (encoded.length > 96) {
            encoded.substring(0, 96) + "..."
        } else {
            encoded
        }
        sender.sendLang("command-serialize-stack-data", encoded.length, preview)
    }

    private fun executeRebuild(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendLang("command-rebuild-player-only")
            return
        }
        val current = player.inventory.itemInMainHand
        if (current.isAir()) {
            sender.sendLang("command-rebuild-empty-hand")
            return
        }
        val stream = Baikiruto.api().readItem(current)
        if (stream == null) {
            sender.sendLang("command-rebuild-not-managed")
            return
        }
        val item = Baikiruto.api().getItem(stream.itemId)
        if (item == null) {
            sender.sendLang("command-rebuild-not-registered", stream.itemId)
            return
        }
        val rebuilt = item.build(
            linkedMapOf<String, Any?>(
                "player" to player,
                "sender" to sender,
                "ctx" to stream.runtimeData,
                "reason" to "manual_rebuild"
            )
        )
        stream.runtimeData.forEach { (key, value) ->
            rebuilt.setRuntimeData(key, value)
        }
        val rebuiltStack = rebuilt.toItemStack().apply {
            amount = current.amount.coerceAtLeast(1)
        }
        player.inventory.setItemInMainHand(rebuiltStack)
        player.updateInventory()
        sender.sendLang("command-rebuild-complete", stream.itemId, stream.versionHash, rebuilt.versionHash)
    }

    private fun executeMenu(sender: CommandSender, groupId: String?) {
        val player = sender as? Player
        if (player == null) {
            sender.sendLang("command-menu-player-only")
            return
        }
        val opened = BaikirutoGroupMenu.open(player, groupId)
        if (!opened) {
            sender.sendLang("command-menu-not-found", groupId?.trim().orEmpty())
        }
    }

    private fun executeGiveSelf(sender: CommandSender, itemId: String, amount: Int) {
        val player = sender as? Player
        if (player == null) {
            sender.sendLang("command-give-player-only")
            return
        }
        executeGive(sender, player, itemId, amount)
    }

    private fun executeGiveFromCommand(
        sender: CommandSender,
        itemId: String,
        targetOrAmount: String?,
        amountRaw: String?
    ) {
        val normalized = targetOrAmount?.trim().orEmpty()
        if (normalized.isEmpty()) {
            executeGiveSelf(sender, itemId, 1)
            return
        }
        val amountCandidate = normalized.toIntOrNull()
        if (amountCandidate != null) {
            if (!amountRaw.isNullOrBlank()) {
                sender.sendLang("command-give-usage")
                return
            }
            executeGiveSelf(sender, itemId, amountCandidate)
            return
        }
        val target = Bukkit.getPlayerExact(normalized) ?: Bukkit.getPlayer(normalized)
        if (target == null) {
            sender.sendLang("command-give-player-offline", normalized)
            return
        }
        val amount = amountRaw?.toIntOrNull() ?: 1
        executeGive(sender, target, itemId, amount)
    }

    private fun executeGive(sender: CommandSender, target: Player, itemId: String, amount: Int) {
        if (Baikiruto.api().getItem(itemId) == null) {
            sender.sendLang("command-give-not-registered", itemId)
            return
        }
        val success = Baikiruto.api().getItemManager().giveItem(
            player = target,
            itemId = itemId,
            amount = amount.coerceAtLeast(1),
            context = mapOf(
                "debug" to true,
                "sender" to sender,
                "target" to target
            )
        )
        if (!success) {
            sender.sendLang("command-give-cancelled")
            return
        }
        sender.sendLang("command-give-success", amount.coerceAtLeast(1), itemId, target.name)
    }
}
