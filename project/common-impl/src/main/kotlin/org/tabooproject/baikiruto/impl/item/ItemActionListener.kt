package org.tabooproject.baikiruto.impl.item

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.event.ItemActionTriggerEvent
import org.tabooproject.baikiruto.core.item.event.ItemInventoryClickActionEvent
import org.tabooproject.baikiruto.impl.item.feature.ItemCombatFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemCooldownFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemDurabilityFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemUseRemainderFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemUniqueFeature
import taboolib.common.platform.Schedule
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.isAir

object ItemActionListener {

    @Schedule(period = 100)
    fun onAsyncTick() {
        Bukkit.getOnlinePlayers().forEach { player ->
            player.inventory.contents.forEachIndexed { index, itemStack ->
                val managed = resolve(itemStack) ?: return@forEachIndexed
                val outcome = dispatch(managed, listOf(ItemScriptTrigger.ASYNC_TICK), player, null)
                if (outcome.changed) {
                    player.inventory.setItem(index, managed.stream.toItemStack())
                }
            }
        }
    }

    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        Baikiruto.api().getItemUpdater().checkUpdate(event.player, event.player.inventory)
        select(event.player)
    }

    @SubscribeEvent
    fun onRespawn(event: PlayerRespawnEvent) {
        Baikiruto.api().getItemUpdater().checkUpdate(event.player, event.player.inventory)
        select(event.player)
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChangeWorld(event: PlayerChangedWorldEvent) {
        Baikiruto.api().getItemUpdater().checkUpdate(event.player, event.player.inventory)
        select(event.player)
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) {
        val managed = resolve(event.player.inventory.getItem(event.newSlot)) ?: return
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.SELECT), event.player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.changed) {
            event.player.inventory.setItem(event.newSlot, managed.stream.toItemStack())
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val managed = resolve(event.item) ?: return
        val triggers = mutableListOf(ItemScriptTrigger.INTERACT)
        when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> triggers += ItemScriptTrigger.LEFT_CLICK
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                triggers += ItemScriptTrigger.RIGHT_CLICK
                triggers += ItemScriptTrigger.USE
            }
            else -> return
        }
        when (val ownership = ensureOwnership(managed, event.player)) {
            is OwnershipValidation.Denied -> {
                event.isCancelled = true
                return
            }
            is OwnershipValidation.Changed -> {
                val rebound = managed.stream.toItemStack()
                if (event.hand == EquipmentSlot.OFF_HAND) {
                    event.player.inventory.setItemInOffHand(rebound)
                } else {
                    event.player.inventory.setItemInMainHand(rebound)
                }
            }
            OwnershipValidation.Pass -> Unit
        }
        if (ItemCooldownFeature.shouldBlock(managed.stream, event.player, triggers)) {
            event.isCancelled = true
            return
        }
        val outcome = dispatch(managed, triggers, event.player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.handled) {
            ItemCooldownFeature.applyCooldown(managed.stream, event.player, triggers)
        }
        if (outcome.changed) {
            val itemStack = managed.stream.toItemStack()
            if (event.hand == EquipmentSlot.OFF_HAND) {
                event.player.inventory.setItemInOffHand(itemStack)
            } else {
                event.player.inventory.setItemInMainHand(itemStack)
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) {
            return
        }
        val managed = resolve(event.player.inventory.itemInMainHand) ?: return
        when (ensureOwnership(managed, event.player)) {
            is OwnershipValidation.Denied -> {
                event.isCancelled = true
                return
            }
            is OwnershipValidation.Changed -> {
                event.player.inventory.setItemInMainHand(managed.stream.toItemStack())
            }
            OwnershipValidation.Pass -> Unit
        }
        val triggers = listOf(ItemScriptTrigger.RIGHT_CLICK_ENTITY)
        if (ItemCooldownFeature.shouldBlock(managed.stream, event.player, triggers)) {
            event.isCancelled = true
            return
        }
        val outcome = dispatch(managed, triggers, event.player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.handled) {
            ItemCooldownFeature.applyCooldown(managed.stream, event.player, triggers)
        }
        if (outcome.changed) {
            event.player.inventory.setItemInMainHand(managed.stream.toItemStack())
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val managed = resolve(player.inventory.itemInMainHand) ?: return
        when (ensureOwnership(managed, player)) {
            is OwnershipValidation.Denied -> {
                event.isCancelled = true
                return
            }
            is OwnershipValidation.Changed -> {
                player.inventory.setItemInMainHand(managed.stream.toItemStack())
            }
            OwnershipValidation.Pass -> Unit
        }
        val triggers = listOf(ItemScriptTrigger.ATTACK)
        if (ItemCooldownFeature.shouldBlock(managed.stream, player, triggers)) {
            event.isCancelled = true
            return
        }
        val outcome = dispatch(managed, triggers, player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.handled) {
            ItemCooldownFeature.applyCooldown(managed.stream, player, triggers)
        }
        if (outcome.changed) {
            player.inventory.setItemInMainHand(managed.stream.toItemStack())
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        val tracked = collectTrackedStreams(player)
        if (tracked.isEmpty()) {
            return
        }
        val resisted = tracked.firstOrNull { trackedItem ->
            ItemCombatFeature.isDamageResistant(trackedItem.stream, trackedItem.slot, event.cause)
        }
        if (resisted != null) {
            event.isCancelled = true
            resisted.stream.setRuntimeData("damage-resistant-last-cause", event.cause.name.lowercase())
            return
        }
        if (player.health - event.finalDamage > 0.0) {
            return
        }
        val protected = tracked.firstOrNull { trackedItem ->
            ItemCombatFeature.canProtectDeath(trackedItem.stream, trackedItem.slot, event.cause)
        } ?: return
        event.isCancelled = true
        player.health = ItemCombatFeature.resolveProtectionHealth(protected.stream, player)
            .coerceIn(0.5, player.maxHealth)
        player.noDamageTicks = player.maximumNoDamageTicks
        protected.stream.setRuntimeData("death-protection-last-cause", event.cause.name.lowercase())
        if (ItemCombatFeature.shouldConsumeProtection(protected.stream)) {
            protected.update(consumeOne(protected.itemStack))
        }
        ItemUseRemainderFeature.resolve(protected.stream, player)?.let { remainder ->
            ItemUseRemainderFeature.give(player, remainder)
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val managed = resolve(player.inventory.itemInMainHand) ?: return
        when (ensureOwnership(managed, player)) {
            is OwnershipValidation.Denied -> {
                event.isCancelled = true
                return
            }
            is OwnershipValidation.Changed -> {
                player.inventory.setItemInMainHand(managed.stream.toItemStack())
            }
            OwnershipValidation.Pass -> Unit
        }
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.BLOCK_BREAK), player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.changed) {
            player.inventory.setItemInMainHand(managed.stream.toItemStack())
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemBreak(event: PlayerItemBreakEvent) {
        val managed = resolve(event.brokenItem) ?: return
        dispatch(managed, listOf(ItemScriptTrigger.ITEM_BREAK), event.player, event)
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: PlayerItemDamageEvent) {
        val managed = resolve(event.item) ?: return
        val durability = ItemDurabilityFeature.applyDamage(managed.stream, event.damage)
        if (durability.applied) {
            event.isCancelled = true
        }
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.DAMAGE), event.player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
        }
        if (durability.destroyed) {
            replacePlayerItem(event.player, event.item, ItemDurabilityFeature.resolveDestroyedItem(managed.stream, event.player))
            return
        }
        if (outcome.changed || durability.applied) {
            replacePlayerItem(event.player, event.item, managed.stream.toItemStack())
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val managed = resolve(event.item) ?: return
        when (val ownership = ensureOwnership(managed, event.player)) {
            is OwnershipValidation.Denied -> {
                event.isCancelled = true
                return
            }
            is OwnershipValidation.Changed -> {
                val rebound = managed.stream.toItemStack()
                if (event.player.inventory.itemInMainHand == event.item) {
                    event.player.inventory.setItemInMainHand(rebound)
                } else if (event.player.inventory.itemInOffHand == event.item) {
                    event.player.inventory.setItemInOffHand(rebound)
                }
            }
            OwnershipValidation.Pass -> Unit
        }
        val triggers = listOf(ItemScriptTrigger.CONSUME, ItemScriptTrigger.USE)
        if (ItemCooldownFeature.shouldBlock(managed.stream, event.player, triggers)) {
            event.isCancelled = true
            return
        }
        val outcome = dispatch(managed, triggers, event.player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.handled) {
            ItemCooldownFeature.applyCooldown(managed.stream, event.player, triggers)
        }
        if (outcome.changed) {
            val updated = managed.stream.toItemStack()
            if (event.player.inventory.itemInMainHand == event.item) {
                event.player.inventory.setItemInMainHand(updated)
            } else if (event.player.inventory.itemInOffHand == event.item) {
                event.player.inventory.setItemInOffHand(updated)
            }
        }
        ItemUseRemainderFeature.resolve(managed.stream, event.player)?.let { remainder ->
            ItemUseRemainderFeature.give(event.player, remainder)
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        resolve(event.mainHandItem)?.let { managed ->
            val outcome = dispatch(managed, listOf(ItemScriptTrigger.SWAP_TO_MAINHAND), event.player, event)
            if (outcome.cancelled) {
                event.isCancelled = true
                return
            }
            if (outcome.changed) {
                event.mainHandItem = managed.stream.toItemStack()
            }
        }
        resolve(event.offHandItem)?.let { managed ->
            val outcome = dispatch(managed, listOf(ItemScriptTrigger.SWAP_TO_OFFHAND), event.player, event)
            if (outcome.cancelled) {
                event.isCancelled = true
                return
            }
            if (outcome.changed) {
                event.offHandItem = managed.stream.toItemStack()
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val managed = resolve(event.itemDrop.itemStack) ?: return
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.DROP), event.player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.changed) {
            event.itemDrop.itemStack = managed.stream.toItemStack()
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val managed = resolve(event.item.itemStack) ?: return
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.PICKUP), player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.changed) {
            event.item.itemStack = managed.stream.toItemStack()
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val currentManaged = resolve(event.currentItem)
        val buttonManaged = if (event.click == ClickType.NUMBER_KEY) resolve(player.inventory.getItem(event.hotbarButton)) else null
        val context = linkedMapOf<String, Any?>(
            "player" to player,
            "sender" to player,
            "event" to event
        )
        val clickEvent = ItemInventoryClickActionEvent(
            currentStream = currentManaged?.stream,
            buttonStream = buttonManaged?.stream,
            player = player,
            source = event,
            context = context
        )
        Baikiruto.api().getItemEventBus().post(clickEvent)
        if (clickEvent.cancelled) {
            event.isCancelled = true
            return
        }
        var changed = clickEvent.saveCurrent || clickEvent.saveButton
        currentManaged?.let { managed ->
            val outcome = dispatch(
                managed = managed,
                triggers = listOf(ItemScriptTrigger.INVENTORY_CLICK),
                player = player,
                event = event,
                contextSeed = clickEvent.context
            )
            if (outcome.cancelled) {
                event.isCancelled = true
                return
            }
            if (outcome.changed) {
                event.currentItem = managed.stream.toItemStack()
                changed = true
            }
        }
        buttonManaged?.let { managed ->
            val outcome = dispatch(
                managed = managed,
                triggers = listOf(ItemScriptTrigger.INVENTORY_CLICK),
                player = player,
                event = event,
                contextSeed = clickEvent.context
            )
            if (outcome.cancelled) {
                event.isCancelled = true
                return
            }
            if (outcome.changed) {
                player.inventory.setItem(event.hotbarButton, managed.stream.toItemStack())
                changed = true
            }
        }
        if (changed) {
            player.updateInventory()
        }
    }

    private fun select(player: Player) {
        player.inventory.contents.forEachIndexed { index, itemStack ->
            val managed = resolve(itemStack) ?: return@forEachIndexed
            val outcome = dispatch(managed, listOf(ItemScriptTrigger.SELECT), player, null)
            if (outcome.changed) {
                player.inventory.setItem(index, managed.stream.toItemStack())
            }
        }
    }

    private fun resolve(itemStack: ItemStack?): ManagedItem? {
        if (itemStack == null || itemStack.isAir()) {
            return null
        }
        val stream = Baikiruto.api().readItem(itemStack) ?: return null
        val item = Baikiruto.api().getItem(stream.itemId) ?: return null
        return ManagedItem(item, stream)
    }

    private fun dispatch(
        managed: ManagedItem,
        triggers: List<ItemScriptTrigger>,
        player: Player?,
        event: Any?,
        contextSeed: Map<String, Any?> = emptyMap()
    ): DispatchOutcome {
        val locale = player?.let { resolveLocale(it) }
        val baseContext = linkedMapOf<String, Any?>()
        baseContext.putAll(contextSeed)
        baseContext.putIfAbsent("player", player)
        baseContext.putIfAbsent("sender", player)
        baseContext.putIfAbsent("event", event)
        if (!locale.isNullOrBlank()) {
            baseContext["locale"] = locale
        }
        val cancellable = event as? Cancellable
        var handled = false
        var save = false
        var cancelled = false
        triggers.forEach { trigger ->
            val triggerContext = LinkedHashMap(baseContext)
            val triggerEvent = ItemActionTriggerEvent(
                stream = managed.stream,
                player = player,
                source = event,
                context = triggerContext,
                trigger = trigger
            )
            Baikiruto.api().getItemEventBus().post(triggerEvent)
            if (triggerEvent.cancelled) {
                cancelled = true
                cancellable?.isCancelled = true
                return@forEach
            }
            if (triggerEvent.save) {
                save = true
            }
            if (!ItemScriptActionDispatcher.hasAction(managed.item, trigger, triggerEvent.context)) {
                return@forEach
            }
            ItemScriptActionDispatcher.dispatch(managed.item, trigger, managed.stream, triggerEvent.context)
            handled = true
        }
        return DispatchOutcome(
            handled = handled,
            save = save,
            cancelled = cancelled
        )
    }

    private fun resolveLocale(player: Player): String? {
        return runCatching { player.locale }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('-', '_')
            ?.lowercase()
    }

    private fun replacePlayerItem(player: Player, source: ItemStack, replacement: ItemStack) {
        if (player.inventory.itemInMainHand == source) {
            player.inventory.setItemInMainHand(replacement)
            return
        }
        if (player.inventory.itemInOffHand == source) {
            player.inventory.setItemInOffHand(replacement)
            return
        }
        val armor = player.inventory.armorContents
        val slot = armor.indexOfFirst { it == source }
        if (slot >= 0) {
            armor[slot] = replacement
            player.inventory.armorContents = armor
        }
    }

    private fun ensureOwnership(managed: ManagedItem, player: Player?): OwnershipValidation {
        val result = ItemUniqueFeature.checkOwnership(managed.stream, player)
        if (!result.allowed) {
            player?.sendMessage(colorize(ItemUniqueFeature.denyMessage(managed.stream)))
            return OwnershipValidation.Denied
        }
        if (result.changed) {
            return OwnershipValidation.Changed
        }
        return OwnershipValidation.Pass
    }

    private fun colorize(message: String): String {
        return ChatColor.translateAlternateColorCodes('&', message)
    }

    private fun collectTrackedStreams(player: Player): List<TrackedItem> {
        val values = mutableListOf<TrackedItem>()
        fun append(slot: String, source: ItemStack?, updater: (ItemStack) -> Unit) {
            if (source == null || source.isAir()) {
                return
            }
            val stream = Baikiruto.api().readItem(source) ?: return
            values += TrackedItem(slot = slot, itemStack = source, stream = stream, update = updater)
        }
        append("MAINHAND", player.inventory.itemInMainHand) { player.inventory.setItemInMainHand(it) }
        append("OFFHAND", player.inventory.itemInOffHand) { player.inventory.setItemInOffHand(it) }
        val armor = player.inventory.armorContents
        armor.getOrNull(0)?.let { boots ->
            append("FEET", boots) { replacement ->
                val copy = player.inventory.armorContents
                if (copy.size > 0) {
                    copy[0] = replacement
                    player.inventory.armorContents = copy
                }
            }
        }
        armor.getOrNull(1)?.let { legs ->
            append("LEGS", legs) { replacement ->
                val copy = player.inventory.armorContents
                if (copy.size > 1) {
                    copy[1] = replacement
                    player.inventory.armorContents = copy
                }
            }
        }
        armor.getOrNull(2)?.let { chest ->
            append("CHEST", chest) { replacement ->
                val copy = player.inventory.armorContents
                if (copy.size > 2) {
                    copy[2] = replacement
                    player.inventory.armorContents = copy
                }
            }
        }
        armor.getOrNull(3)?.let { head ->
            append("HEAD", head) { replacement ->
                val copy = player.inventory.armorContents
                if (copy.size > 3) {
                    copy[3] = replacement
                    player.inventory.armorContents = copy
                }
            }
        }
        return values
    }

    private fun consumeOne(source: ItemStack): ItemStack {
        if (source.amount <= 1) {
            return ItemStack(org.bukkit.Material.AIR)
        }
        return source.clone().apply {
            amount = source.amount - 1
        }
    }

    private sealed class OwnershipValidation {
        object Pass : OwnershipValidation()
        object Denied : OwnershipValidation()
        object Changed : OwnershipValidation()
    }

    private data class ManagedItem(
        val item: Item,
        val stream: ItemStream
    )

    private data class DispatchOutcome(
        val handled: Boolean,
        val save: Boolean,
        val cancelled: Boolean
    ) {

        val changed: Boolean
            get() = handled || save
    }

    private data class TrackedItem(
        val slot: String,
        val itemStack: ItemStack,
        val stream: ItemStream,
        val update: (ItemStack) -> Unit
    )
}

