package org.tabooproject.baikiruto.impl.command

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import org.tabooproject.baikiruto.impl.ops.BaikirutoDiagnostics
import org.tabooproject.baikiruto.impl.ops.BaikirutoReloader
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand

@CommandHeader(
    name = "baikiruto",
    aliases = ["bkr"],
    permission = "baikiruto.command"
)
object BaikirutoCommand {

    private const val DEFAULT_ITEM_ID = "example:all_features"

    @CommandBody(permission = "baikiruto.command")
    val main = mainCommand {
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage("Baikiruto is running. Try /baikiruto debug build <itemId> or /baikiruto reload.")
        }
    }

    @CommandBody(permission = "baikiruto.command.reload")
    val reload = subCommand {
        literal("items") {
            execute<CommandSender> { sender, _, _ ->
                sender.sendMessage(BaikirutoReloader.reloadItems())
            }
        }
        literal("scripts") {
            execute<CommandSender> { sender, _, _ ->
                sender.sendMessage(BaikirutoReloader.reloadScripts())
            }
        }
        literal("debug") {
            execute<CommandSender> { sender, _, _ ->
                sender.sendMessage("Reload debug snapshot:")
                BaikirutoDiagnostics.lines().forEach(sender::sendMessage)
            }
        }
        execute<CommandSender> { sender, _, _ ->
            sender.sendMessage(BaikirutoReloader.reloadAll())
        }
    }

    @CommandBody(permission = "baikiruto.command.debug")
    val debug = subCommand {
        literal("build") {
            dynamic("itemId") {
                suggestion<CommandSender>(uncheck = true) { _, _ ->
                    Baikiruto.api().getItemRegistry().keys().sorted()
                }
                execute<CommandSender> { sender, context, _ ->
                    val itemId = context["itemId"]
                    executeBuild(sender, itemId)
                }
            }
            execute<CommandSender> { sender, _, _ ->
                executeBuild(sender, DEFAULT_ITEM_ID)
            }
        }
        literal("give") {
            dynamic("itemId") {
                suggestion<CommandSender>(uncheck = true) { _, _ ->
                    Baikiruto.api().getItemRegistry().keys().sorted()
                }
                dynamic("amount") {
                    execute<CommandSender> { sender, context, _ ->
                        executeGive(sender, context["itemId"], context["amount"]?.toIntOrNull() ?: 1)
                    }
                }
                execute<CommandSender> { sender, context, _ ->
                    executeGive(sender, context["itemId"], 1)
                }
            }
            execute<CommandSender> { sender, _, _ ->
                executeGive(sender, DEFAULT_ITEM_ID, 1)
            }
        }
        literal("metrics") {
            execute<CommandSender> { sender, _, _ ->
                BaikirutoDiagnostics.lines().forEach(sender::sendMessage)
            }
        }
        literal("read") {
            execute<CommandSender> { sender, _, _ ->
                executeRead(sender)
            }
        }
        literal("update") {
            execute<CommandSender> { sender, _, _ ->
                executeUpdate(sender)
            }
        }
    }

    @CommandBody(permission = "baikiruto.command.debug")
    val selfcheck = subCommand {
        execute<CommandSender> { sender, _, _ ->
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

    private fun executeGive(sender: CommandSender, itemId: String, amount: Int) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only player can receive managed item.")
            return
        }
        if (Baikiruto.api().getItem(itemId) == null) {
            sender.sendMessage("Item '$itemId' is not registered.")
            return
        }
        val success = Baikiruto.api().getItemManager().giveItem(
            player = player,
            itemId = itemId,
            amount = amount.coerceAtLeast(1),
            context = mapOf("debug" to true)
        )
        if (!success) {
            sender.sendMessage("Give cancelled by listener.")
            return
        }
        sender.sendMessage("Given ${amount.coerceAtLeast(1)}x $itemId.")
    }
}
