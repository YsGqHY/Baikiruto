package org.tabooproject.baikiruto.impl.hook

import dev.rosewood.roseloot.api.RoseLootAPI
import dev.rosewood.roseloot.loot.context.LootContext
import dev.rosewood.roseloot.loot.item.ItemGenerativeLootItem
import dev.rosewood.roseloot.provider.NumberProvider
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.Ghost
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info

object RoseLootHook {

    private const val PLUGIN_NAME = "RoseLoot"
    private const val LOOT_ITEM_TYPE = "baikiruto"

    @Volatile
    private var registered = false

    @Awake(LifeCycle.ACTIVE)
    private fun register() {
        registerIfAvailable()
    }

    @Ghost
    @SubscribeEvent
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals(PLUGIN_NAME, ignoreCase = true)) {
            registerIfAvailable()
        }
    }

    private fun registerIfAvailable() {
        if (!BaikirutoSettings.roseLootHookEnabled || registered) {
            return
        }
        if (!isPluginEnabled()) {
            return
        }
        val success = runCatching {
            RoseLootAPI.getInstance().registerCustomLootItem(LOOT_ITEM_TYPE, BaikirutoRoseLootItem::fromSection)
        }.onFailure { throwable ->
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][ROSE_LOOT] register failed: ${throwable.message}") }
        }.getOrDefault(false)
        if (success || !registered) {
            registered = true
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][ROSE_LOOT] registered loot item type: $LOOT_ITEM_TYPE") }
        }
    }

    private fun isPluginEnabled(): Boolean {
        return try {
            Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)
        } catch (_: Throwable) {
            false
        }
    }

    private class BaikirutoRoseLootItem(
        private val itemId: String,
        private val amount: NumberProvider,
        private val maxAmount: NumberProvider
    ) : ItemGenerativeLootItem {

        override fun generate(context: LootContext): List<ItemStack> {
            val finalAmount = amount.getInteger(context)
                .coerceAtLeast(0)
                .coerceAtMost(maxAmount.getInteger(context).coerceAtLeast(0))
            if (finalAmount <= 0) {
                context.addPlaceholder("item_amount", 0)
                return emptyList()
            }
            val itemStack = Baikiruto.api().getItemManager().generateItemStack(itemId, buildContext(context))
            if (itemStack == null) {
                BaikirutoSettings.debug { info("[Baikiruto][DEBUG][ROSE_LOOT] item not found: $itemId") }
                context.addPlaceholder("item_amount", 0)
                return emptyList()
            }
            val generated = splitItemStack(itemStack, finalAmount)
            context.addPlaceholder("item_amount", generated.sumOf { it.amount })
            return generated
        }

        override fun getAllItems(context: LootContext): List<ItemStack> {
            return generate(context)
        }

        private fun buildContext(context: LootContext): Map<String, Any?> {
            val values = linkedMapOf<String, Any?>(
                "roseLootContext" to context,
                "rose_loot_context" to context,
                "item_id" to itemId
            )
            context.lootingPlayer.ifPresent { player ->
                values["player"] = player
                values["sender"] = player
                values["looter"] = player
            }
            context.currentLootTable.ifPresent { table ->
                values["roseLootTable"] = table
                values["rose_loot_table"] = table
            }
            return values
        }

        private fun splitItemStack(template: ItemStack, amount: Int): List<ItemStack> {
            val maxStackSize = template.maxStackSize.coerceAtLeast(1)
            val result = ArrayList<ItemStack>()
            var remaining = amount
            while (remaining > 0) {
                val stack = template.clone()
                stack.amount = remaining.coerceAtMost(maxStackSize)
                result += stack
                remaining -= stack.amount
            }
            return result
        }

        companion object {

            fun fromSection(section: ConfigurationSection): ItemGenerativeLootItem? {
                val itemId = section.getString("item")
                    ?: section.getString("id")
                    ?: return null
                val amount = NumberProvider.fromSection(section, "amount", 1)
                val maxAmount = NumberProvider.fromSection(section, "max-amount", Int.MAX_VALUE)
                return BaikirutoRoseLootItem(itemId, amount, maxAmount)
            }
        }
    }
}
