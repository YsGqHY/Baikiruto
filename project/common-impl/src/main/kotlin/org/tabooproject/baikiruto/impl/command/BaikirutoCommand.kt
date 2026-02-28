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
import taboolib.platform.util.isAir

@CommandHeader(
    name = "baikiruto",
    aliases = ["bkr", "bai"],
    permission = "baikiruto.command"
)
object BaikirutoCommand {

    private const val DEFAULT_ITEM_ID = "example:all_features"

    @CommandBody(permission = "baikiruto.command")
    val main = mainCommand {
        exec<CommandSender> {
            sender.sendMessage("Baikiruto is running. Try /baikiruto debug build <itemId> or /baikiruto reload.")
        }
    }

    @CommandBody(permission = "baikiruto.command.reload")
    val reload = subCommand {
        literal("items") {
            exec<CommandSender> {
                sender.sendMessage(BaikirutoReloader.reloadItems())
            }
        }
        literal("scripts") {
            exec<CommandSender> {
                sender.sendMessage(BaikirutoReloader.reloadScripts())
            }
        }
        literal("debug") {
            exec<CommandSender> {
                sender.sendMessage("Reload debug snapshot:")
                BaikirutoDiagnostics.lines().forEach(sender::sendMessage)
            }
        }
        exec<CommandSender> {
            sender.sendMessage(BaikirutoReloader.reloadAll())
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
            sender.sendMessage("Baikiruto selfcheck:")
            BaikirutoDiagnostics.lines().forEach(sender::sendMessage)
            sender.sendMessage("Registered items: ${ItemDefinitionLoader.loadedIds().joinToString(", ")}")
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
            sender.sendMessage("Build failed: item '$itemId' is not registered.")
            return
        }
        sender.sendMessage("Build success: $itemId -> ${result.type} x${result.amount}")
    }

    private fun executeList(sender: CommandSender, keyword: String?) {
        val allItems = Baikiruto.api().getItemRegistry().keys().sorted()
        if (allItems.isEmpty()) {
            sender.sendMessage("No managed items loaded.")
            return
        }
        val normalizedKeyword = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val filtered = if (normalizedKeyword == null) {
            allItems
        } else {
            allItems.filter { id -> id.contains(normalizedKeyword, ignoreCase = true) }
        }
        if (filtered.isEmpty()) {
            sender.sendMessage("No managed item matched keyword '$normalizedKeyword'.")
            return
        }
        sender.sendMessage("Managed items (${filtered.size}/${allItems.size}):")
        filtered.forEach { id ->
            sender.sendMessage("- $id")
        }
    }

    private fun executeRead(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only player can inspect held item stream.")
            return
        }
        val stream = Baikiruto.api().readItem(player.inventory.itemInMainHand)
        if (stream == null) {
            sender.sendMessage("Current main-hand item is not managed by Baikiruto.")
            return
        }
        sender.sendMessage("Baikiruto stream:")
        sender.sendMessage("- id: ${stream.itemId}")
        sender.sendMessage("- version: ${stream.versionHash}")
        sender.sendMessage("- metas: ${if (stream.metaHistory.isEmpty()) "[]" else stream.metaHistory.joinToString(", ", "[", "]")}")
        sender.sendMessage("- signals: ${if (stream.signals.isEmpty()) "[]" else stream.signals.joinToString(", ", "[", "]")}")
        if (stream.runtimeData.isEmpty()) {
            sender.sendMessage("- runtimeData: {}")
            return
        }
        sender.sendMessage("- runtimeData:")
        stream.runtimeData.entries.sortedBy { it.key }.forEach { (key, value) ->
            sender.sendMessage("  - $key: $value")
        }
    }

    private fun executeUpdate(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only player can run inventory update check.")
            return
        }
        val updated = Baikiruto.api().getItemUpdater().checkUpdate(player, player.inventory)
        sender.sendMessage("Inventory update check complete. updated=$updated")
    }

    private fun executeSerialize(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only player can serialize held item.")
            return
        }
        val item = player.inventory.itemInMainHand
        if (item.isAir()) {
            sender.sendMessage("Main-hand item is empty.")
            return
        }
        val serialized = Baikiruto.api().getItemSerializer().serialize(item)
        sender.sendMessage("Serialized item:")
        sender.sendMessage("- itemId: ${serialized.itemId}")
        sender.sendMessage("- amount: ${serialized.amount}")
        sender.sendMessage("- version: ${serialized.versionHash}")
        sender.sendMessage("- metas: ${if (serialized.metaHistory.isEmpty()) "[]" else serialized.metaHistory.joinToString(", ", "[", "]")}")
        if (serialized.runtimeData.isEmpty()) {
            sender.sendMessage("- runtimeData: {}")
        } else {
            sender.sendMessage("- runtimeData:")
            serialized.runtimeData.entries.sortedBy { it.key }.forEach { (key, value) ->
                sender.sendMessage("  - $key: $value")
            }
        }
        val encoded = serialized.itemStackData
        val preview = if (encoded.length > 96) {
            encoded.substring(0, 96) + "..."
        } else {
            encoded
        }
        sender.sendMessage("- itemStackData(length=${encoded.length}): $preview")
    }

    private fun executeRebuild(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only player can rebuild held item.")
            return
        }
        val current = player.inventory.itemInMainHand
        if (current.isAir()) {
            sender.sendMessage("Main-hand item is empty.")
            return
        }
        val stream = Baikiruto.api().readItem(current)
        if (stream == null) {
            sender.sendMessage("Current main-hand item is not managed by Baikiruto.")
            return
        }
        val item = Baikiruto.api().getItem(stream.itemId)
        if (item == null) {
            sender.sendMessage("Managed item '${stream.itemId}' is no longer registered.")
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
        sender.sendMessage("Rebuild complete: ${stream.itemId} ${stream.versionHash} -> ${rebuilt.versionHash}")
    }

    private fun executeMenu(sender: CommandSender, groupId: String?) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only player can open group menu.")
            return
        }
        val opened = BaikirutoGroupMenu.open(player, groupId)
        if (!opened) {
            sender.sendMessage("Group '${groupId?.trim()}' does not exist.")
        }
    }

    private fun executeGiveSelf(sender: CommandSender, itemId: String, amount: Int) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only player can receive managed item.")
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
                sender.sendMessage("Usage: /baikiruto give <itemId> [player] [amount]")
                return
            }
            executeGiveSelf(sender, itemId, amountCandidate)
            return
        }
        val target = Bukkit.getPlayerExact(normalized) ?: Bukkit.getPlayer(normalized)
        if (target == null) {
            sender.sendMessage("Player '$normalized' is not online.")
            return
        }
        val amount = amountRaw?.toIntOrNull() ?: 1
        executeGive(sender, target, itemId, amount)
    }

    private fun executeGive(sender: CommandSender, target: Player, itemId: String, amount: Int) {
        if (Baikiruto.api().getItem(itemId) == null) {
            sender.sendMessage("Item '$itemId' is not registered.")
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
            sender.sendMessage("Give cancelled by listener.")
            return
        }
        sender.sendMessage("Given ${amount.coerceAtLeast(1)}x $itemId to ${target.name}.")
    }
}
