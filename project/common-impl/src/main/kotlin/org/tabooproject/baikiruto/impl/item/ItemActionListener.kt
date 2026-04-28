package org.tabooproject.baikiruto.impl.item

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.Cancellable
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
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
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.event.ItemActionTriggerEvent
import org.tabooproject.baikiruto.core.item.event.ItemAsyncTickActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemAttackActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemBlockBreakActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemConsumeActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemDamageActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemDeathActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemDropActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemEquipActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemHurtActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemInteractActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemInteractEntityActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemInventoryClickActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemInventoryClickTriggerEvent
import org.tabooproject.baikiruto.core.item.event.ItemItemBreakActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemJumpActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemKillActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemLeftClickActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemPickupActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemProjectileHitActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemRespawnActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemRightClickActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemSelectActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemShootActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemSneakActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemSprintActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemSwapToMainhandActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemSwapToOffhandActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemUnequipActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemUseActionEvent
import org.tabooproject.baikiruto.impl.item.feature.ItemCombatFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemCooldownFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemDurabilityFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemUseRemainderFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemUniqueFeature
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.platform.Schedule
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.event.PlayerJumpEvent
import taboolib.platform.util.isAir
import taboolib.platform.util.sendLang

object ItemActionListener {

    private var asyncTickClock = 0L

    @Schedule(period = 1)
    fun onAsyncTick() {
        asyncTickClock += 1L
        if (!BaikirutoSettings.asyncTickEnabled) {
            return
        }
        val currentTick = asyncTickClock
        Bukkit.getOnlinePlayers().forEach { player ->
            player.inventory.contents.forEachIndexed { index, itemStack ->
                val managed = resolve(itemStack) ?: return@forEachIndexed
                val slot = resolveAsyncTickSlot(player, index)
                if (!shouldDispatchAsyncTick(managed, player, slot, index, currentTick)) {
                    return@forEachIndexed
                }
                val outcome = dispatch(
                    managed = managed,
                    triggers = listOf(ItemScriptTrigger.ASYNC_TICK),
                    player = player,
                    event = null,
                    contextSeed = linkedMapOf(
                        "slot" to slot,
                        "slot_index" to index
                    )
                )
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
        dispatchTracked(event.player, listOf(ItemScriptTrigger.RESPAWN), event)
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

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        // 右键空气/方块会触发两次（主手+副手），只处理主手
        if (event.hand == EquipmentSlot.OFF_HAND && event.item == null) return
        BaikirutoSettings.debug {
            val isArmor = resolveArmorSlot(event.item)
            if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
                info("[Baikiruto][DEBUG][INTERACT_RAW] player=${event.player.name} action=${event.action} hand=${event.hand} item=${event.item?.type} isArmor=$isArmor isCancelled=${event.isCancelled} useItemInHand=${event.useItemInHand()}")
            }
        }
        if (event.isCancelled && event.action != Action.LEFT_CLICK_AIR && event.action != Action.RIGHT_CLICK_AIR) return
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
        // 右键穿戴装备绑定拦截
        val isRightClick = event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK
        val armorSlot = if (isRightClick) resolveArmorSlot(event.item) else null
        if (isRightClick && armorSlot != null) {
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_RIGHTCLICK] player=${event.player.name} action=${event.action} item=${managed.item.id} slot=$armorSlot checking ownership...") }
        }
        when (ensureOwnership(managed, event.player)) {
            is OwnershipValidation.Denied -> {
                event.isCancelled = true
                if (isRightClick && armorSlot != null) {
                    BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_RIGHTCLICK] player=${event.player.name} item=${managed.item.id} slot=$armorSlot -> DENIED") }
                }
                return
            }
            is OwnershipValidation.Changed -> {
                val rebound = managed.stream.toItemStack()
                if (event.hand == EquipmentSlot.OFF_HAND) {
                    event.player.inventory.setItemInOffHand(rebound)
                } else {
                    event.player.inventory.setItemInMainHand(rebound)
                }
                if (isRightClick && armorSlot != null) {
                    scheduleArmorBindSync(event.player, managed, rebound)
                    BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_RIGHTCLICK] player=${event.player.name} item=${managed.item.id} slot=$armorSlot -> CHANGED (auto-bind)") }
                }
            }
            OwnershipValidation.Pass -> {
                if (isRightClick && armorSlot != null) {
                    BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_RIGHTCLICK] player=${event.player.name} item=${managed.item.id} slot=$armorSlot -> PASS") }
                }
            }
        }
        // 左键交互：软阻断（冷却期间跳过脚本但不取消事件）
        // 右键交互：硬阻断（冷却期间取消事件）
        val isLeftClick = event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK
        if (isLeftClick) {
            if (ItemCooldownFeature.shouldThrottle(managed.stream, event.player, triggers)) {
                return
            }
        } else {
            if (ItemCooldownFeature.shouldBlock(managed.stream, event.player, triggers)) {
                event.isCancelled = true
                return
            }
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
        // 软阻断：冷却期间跳过脚本但保留原版伤害
        if (ItemCooldownFeature.shouldThrottle(managed.stream, player, triggers)) {
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
        // 从当前背包重新读取物品，避免 onAttack 已替换主手后 event.item 引用过时
        val currentItem = findCurrentItem(event.player, event.item) ?: event.item
        val managed = resolve(currentItem) ?: return
        val durability = ItemDurabilityFeature.applyDamage(managed.stream, event.damage)
        if (durability.applied) {
            event.isCancelled = true
        }
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.DAMAGE), event.player, event)
        if (outcome.cancelled) {
            event.isCancelled = true
        }
        if (durability.destroyed) {
            replacePlayerItem(event.player, currentItem, ItemDurabilityFeature.resolveDestroyedItem(managed.stream, event.player))
            return
        }
        if (outcome.changed || durability.applied) {
            replacePlayerItem(event.player, currentItem, managed.stream.toItemStack())
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

    // ── 玩家死亡 ──

    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        dispatchTracked(player, listOf(ItemScriptTrigger.DEATH), event)
    }

    // ── 击杀实体 ──

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val managed = resolve(killer.inventory.itemInMainHand) ?: return
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.KILL), killer, event)
        if (outcome.changed) {
            killer.inventory.setItemInMainHand(managed.stream.toItemStack())
        }
    }

    // ── 玩家受伤 ──

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerHurt(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        // 避免与 onPlayerDamage 中的 damage-resistant/death-protection 逻辑冲突
        // 此处仅做脚本触发，不取消事件
        dispatchTracked(player, listOf(ItemScriptTrigger.HURT), event)
    }

    // ── 发射弹射物 ──

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShoot(event: ProjectileLaunchEvent) {
        val shooter = event.entity.shooter as? Player ?: return
        val managed = resolve(shooter.inventory.itemInMainHand) ?: return
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.SHOOT), shooter, event)
        if (outcome.cancelled) {
            event.isCancelled = true
            return
        }
        if (outcome.changed) {
            shooter.inventory.setItemInMainHand(managed.stream.toItemStack())
        }
    }

    // ── 弹射物命中 ──

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val shooter = event.entity.shooter as? Player ?: return
        val managed = resolve(shooter.inventory.itemInMainHand) ?: return
        val outcome = dispatch(managed, listOf(ItemScriptTrigger.PROJECTILE_HIT), shooter, event)
        if (outcome.changed) {
            shooter.inventory.setItemInMainHand(managed.stream.toItemStack())
        }
    }

    // ── 潜行切换 ──

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        dispatchTracked(event.player, listOf(ItemScriptTrigger.SNEAK), event)
    }

    // ── 疾跑切换 ──

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSprint(event: PlayerToggleSprintEvent) {
        dispatchTracked(event.player, listOf(ItemScriptTrigger.SPRINT), event)
    }

    // ── 跳跃检测 ──

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onJump(event: PlayerJumpEvent) {
        dispatchTracked(event.player, listOf(ItemScriptTrigger.JUMP), event)
    }

    // ── 装备穿戴/脱下检测 ──

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArmorClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        // 仅处理玩家背包中的装备槽操作
        if (event.clickedInventory?.type != InventoryType.PLAYER && event.clickedInventory?.type != InventoryType.CRAFTING) return
        val armorSlotIndex = when (event.rawSlot) {
            5 -> "HEAD"
            6 -> "CHEST"
            7 -> "LEGS"
            8 -> "FEET"
            else -> null
        }
        if (armorSlotIndex != null) {
            BaikirutoSettings.debug {
                info("[Baikiruto][DEBUG][EQUIP_CLICK] player=${player.name} rawSlot=${event.rawSlot} slot=$armorSlotIndex click=${event.click} currentItem=${event.currentItem?.type} cursor=${event.cursor?.type}")
            }
            // 直接点击装备槽
            if (handleEquipSlotChange(player, armorSlotIndex, event.currentItem, event.cursor, event)) {
                event.isCancelled = true
                BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_CLICK] player=${player.name} slot=$armorSlotIndex -> CANCELLED (ownership denied)") }
            }
            return
        }
        // Shift-click 装备到装备槽
        if (event.isShiftClick && event.currentItem != null) {
            val targetSlot = resolveArmorSlot(event.currentItem) ?: return
            val managed = resolve(event.currentItem) ?: return
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_SHIFT] player=${player.name} item=${managed.item.id} targetSlot=$targetSlot") }
            // 绑定检查：阻止非绑定玩家穿戴
            var ownershipChanged = false
            when (ensureOwnership(managed, player)) {
                is OwnershipValidation.Denied -> {
                    event.isCancelled = true
                    BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_SHIFT] player=${player.name} item=${managed.item.id} -> CANCELLED (ownership denied)") }
                    return
                }
                is OwnershipValidation.Changed -> ownershipChanged = true
                OwnershipValidation.Pass -> Unit
            }
            val context = linkedMapOf<String, Any?>("slot" to targetSlot)
            val outcome = dispatch(managed, listOf(ItemScriptTrigger.EQUIP), player, event, context)
            if (ownershipChanged || outcome.changed) {
                // 绑定变更或脚本修改后，延迟一 tick 回写到目标装备槽
                val updated = managed.stream.toItemStack()
                val targetSlotCopy = targetSlot
                submit(delay = 1) {
                    setArmorSlot(player, targetSlotCopy, updated)
                    player.updateInventory()
                }
            }
        }
    }

    /**
     * 处理装备槽位变更（直接点击装备槽）。
     * @return true 表示操作被拒绝（绑定检查失败），调用方应取消事件
     */
    private fun handleEquipSlotChange(
        player: Player,
        slot: String,
        currentItem: ItemStack?,
        cursorItem: ItemStack?,
        event: Any?
    ): Boolean {
        // 穿戴前先检查绑定，避免在拒绝时误触发 UNEQUIP 脚本
        val cursorManaged = resolve(cursorItem)
        var ownershipChanged = false
        if (cursorManaged != null) {
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_SLOT] player=${player.name} slot=$slot cursorItem=${cursorManaged.item.id} checking ownership...") }
            when (ensureOwnership(cursorManaged, player)) {
                is OwnershipValidation.Denied -> {
                    BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_SLOT] player=${player.name} slot=$slot -> DENIED") }
                    return true
                }
                is OwnershipValidation.Changed -> {
                    ownershipChanged = true
                    BaikirutoSettings.debug { info("[Baikiruto][DEBUG][EQUIP_SLOT] player=${player.name} slot=$slot -> CHANGED (auto-bind)") }
                }
                OwnershipValidation.Pass -> Unit
            }
        }
        // 脱下：当前槽位有物品
        resolve(currentItem)?.let { managed ->
            val context = linkedMapOf<String, Any?>("slot" to slot)
            val outcome = dispatch(managed, listOf(ItemScriptTrigger.UNEQUIP), player, event, context)
            if (outcome.changed) {
                // 在 Bukkit 执行默认 click 处理之前替换 currentItem，
                // 使物品无论最终去了光标还是被 shift-click 到其他位置，都携带正确的数据
                val updated = managed.stream.toItemStack()
                val inventoryEvent = event as? InventoryClickEvent
                if (inventoryEvent != null) {
                    inventoryEvent.currentItem = updated
                }
            }
        }
        // 穿戴：光标上有物品放入槽位
        if (cursorManaged != null) {
            val context = linkedMapOf<String, Any?>("slot" to slot)
            val outcome = dispatch(cursorManaged, listOf(ItemScriptTrigger.EQUIP), player, event, context)
            // 绑定变更或脚本修改了 stream 时，将数据回写到光标物品
            if (ownershipChanged || outcome.changed) {
                val updated = cursorManaged.stream.toItemStack()
                val inventoryEvent = event as? InventoryClickEvent
                if (inventoryEvent != null) {
                    inventoryEvent.setCursor(updated)
                } else {
                    val slotCopy = slot
                    submit(delay = 1) {
                        setArmorSlot(player, slotCopy, updated)
                        player.updateInventory()
                    }
                }
            }
        }
        return false
    }

    private fun resolveArmorSlot(item: ItemStack?): String? {
        if (item == null) return null
        val typeName = item.type.name
        return when {
            typeName.endsWith("_HELMET") || typeName.endsWith("_CAP") || typeName == "PLAYER_HEAD" || typeName == "SKELETON_SKULL" || typeName == "ZOMBIE_HEAD" || typeName == "CREEPER_HEAD" || typeName == "DRAGON_HEAD" || typeName == "CARVED_PUMPKIN" || typeName == "TURTLE_HELMET" -> "HEAD"
            typeName.endsWith("_CHESTPLATE") || typeName == "ELYTRA" -> "CHEST"
            typeName.endsWith("_LEGGINGS") -> "LEGS"
            typeName.endsWith("_BOOTS") -> "FEET"
            else -> null
        }
    }

    private fun setArmorSlot(player: Player, slot: String, itemStack: ItemStack) {
        when (slot) {
            "HEAD" -> player.inventory.helmet = itemStack
            "CHEST" -> player.inventory.chestplate = itemStack
            "LEGS" -> player.inventory.leggings = itemStack
            "FEET" -> player.inventory.boots = itemStack
        }
    }

    /**
     * 右键穿戴装备时，Bukkit 在事件处理之后才将物品从主手移到装备槽。
     * 延迟一 tick 检查目标装备槽是否已被穿戴，如果是则用带绑定数据的版本覆盖。
     */
    private fun scheduleArmorBindSync(player: Player, managed: ManagedItem, updated: ItemStack) {
        val targetSlot = resolveArmorSlot(updated) ?: return
        submit(delay = 1) {
            if (!player.isOnline) return@submit
            val current = when (targetSlot) {
                "HEAD" -> player.inventory.helmet
                "CHEST" -> player.inventory.chestplate
                "LEGS" -> player.inventory.leggings
                "FEET" -> player.inventory.boots
                else -> null
            }
            // 只有当装备槽确实被穿上了同类物品时才覆盖
            if (current != null && !current.isAir() && current.type == updated.type) {
                setArmorSlot(player, targetSlot, updated)
            }
        }
    }

    /**
     * 遍历玩家全身装备（主手/副手/头盔/胸甲/护腿/靴子），
     * 对每个 Baikiruto 物品分别 dispatch 触发器，context 中注入 slot 变量。
     */
    private fun dispatchTracked(player: Player, triggers: List<ItemScriptTrigger>, event: Any?) {
        val tracked = collectTrackedStreams(player)
        if (tracked.isEmpty()) return
        tracked.forEach { trackedItem ->
            val item = Baikiruto.api().getItem(trackedItem.stream.itemId) ?: return@forEach
            val managed = ManagedItem(item, trackedItem.stream)
            val context = linkedMapOf<String, Any?>("slot" to trackedItem.slot)
            val outcome = dispatch(managed, triggers, player, event, context)
            if (outcome.changed) {
                trackedItem.update(managed.stream.toItemStack())
            }
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

    private fun shouldDispatchAsyncTick(managed: ManagedItem, player: Player, slot: String, slotIndex: Int, currentTick: Long): Boolean {
        if (!ItemAsyncTickPolicy.resolveEnabled(managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_ENABLED))) {
            return false
        }
        val baseContext = linkedMapOf<String, Any?>(
            "player" to player,
            "sender" to player,
            "event" to null,
            "slot" to slot,
            "slot_index" to slotIndex
        )
        if (!ItemScriptActionDispatcher.hasAction(managed.item, ItemScriptTrigger.ASYNC_TICK, baseContext)) {
            return false
        }
        val conditionState = resolveAsyncTickConditionState(player, slot)
        if (!ItemAsyncTickPolicy.matchesConditions(
                conditions = mapOf(
                    ItemAsyncTickPolicy.KEY_CONDITION_SNEAKING to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_SNEAKING),
                    ItemAsyncTickPolicy.KEY_CONDITION_SPRINTING to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_SPRINTING),
                    ItemAsyncTickPolicy.KEY_CONDITION_SWIMMING to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_SWIMMING),
                    ItemAsyncTickPolicy.KEY_CONDITION_GLIDING to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_GLIDING),
                    ItemAsyncTickPolicy.KEY_CONDITION_FLYING to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_FLYING),
                    ItemAsyncTickPolicy.KEY_CONDITION_ON_GROUND to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_ON_GROUND),
                    ItemAsyncTickPolicy.KEY_CONDITION_IN_VEHICLE to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_IN_VEHICLE),
                    ItemAsyncTickPolicy.KEY_CONDITION_BURNING to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_BURNING),
                    ItemAsyncTickPolicy.KEY_CONDITION_BLOCKING to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_BLOCKING),
                    ItemAsyncTickPolicy.KEY_CONDITION_SLOTS to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_SLOTS),
                    ItemAsyncTickPolicy.KEY_CONDITION_WORLDS to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_WORLDS),
                    ItemAsyncTickPolicy.KEY_CONDITION_GAME_MODES to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_GAME_MODES),
                    ItemAsyncTickPolicy.KEY_CONDITION_PERMISSIONS to managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_CONDITION_PERMISSIONS)
                ),
                state = conditionState
            )
        ) {
            return false
        }
        val interval = ItemAsyncTickPolicy.resolveInterval(
            BaikirutoSettings.asyncTickDefaultInterval,
            managed.stream.getRuntimeData(ItemAsyncTickPolicy.KEY_INTERVAL)
        )
        val seed = ItemAsyncTickPolicy.stableSeed(player.uniqueId.toString(), slotIndex, managed.item.id)
        return ItemAsyncTickPolicy.shouldTrigger(currentTick, interval, seed)
    }

    private fun resolveAsyncTickSlot(player: Player, slotIndex: Int): String {
        return when {
            slotIndex == player.inventory.heldItemSlot -> "MAINHAND"
            slotIndex in 0..8 -> "HOTBAR"
            slotIndex in 9..35 -> "INVENTORY"
            slotIndex == 36 -> "FEET"
            slotIndex == 37 -> "LEGS"
            slotIndex == 38 -> "CHEST"
            slotIndex == 39 -> "HEAD"
            slotIndex == 40 -> "OFFHAND"
            else -> "INVENTORY"
        }
    }

    private fun resolveAsyncTickConditionState(player: Player, slot: String): ItemAsyncTickPolicy.ConditionState {
        return ItemAsyncTickPolicy.ConditionState(
            slot = slot,
            sneaking = player.isSneaking,
            sprinting = player.isSprinting,
            swimming = readPlayerBoolean(player, "isSwimming"),
            gliding = readPlayerBoolean(player, "isGliding"),
            flying = player.isFlying,
            onGround = readPlayerBoolean(player, "isOnGround"),
            inVehicle = player.isInsideVehicle,
            burning = player.fireTicks > 0,
            blocking = readPlayerBoolean(player, "isBlocking"),
            world = player.world.name,
            gameMode = player.gameMode.name,
            hasPermission = { permission -> player.hasPermission(permission) }
        )
    }

    private fun readPlayerBoolean(player: Player, methodName: String): Boolean {
        return runCatching {
            val method = player.javaClass.methods.firstOrNull { candidate ->
                candidate.name == methodName && candidate.parameterCount == 0
            } ?: return@runCatching false
            method.invoke(player) as? Boolean ?: false
        }.getOrDefault(false)
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
        (managed.stream as? DefaultItemStream)?.rememberInvocationContext(baseContext)
        val cancellable = event as? Cancellable
        var handled = false
        var save = false
        var cancelled = false
        triggers.forEach { trigger ->
            val triggerContext = LinkedHashMap(baseContext)
            val triggerEvent = createTriggerEvent(
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

    private fun createTriggerEvent(
        stream: ItemStream,
        player: Player?,
        source: Any?,
        context: MutableMap<String, Any?>,
        trigger: ItemScriptTrigger
    ): ItemActionTriggerEvent {
        return when (trigger) {
            ItemScriptTrigger.SELECT -> ItemSelectActionEvent(stream, player, source, context)
            ItemScriptTrigger.ASYNC_TICK -> ItemAsyncTickActionEvent(stream, player, source, context)
            ItemScriptTrigger.INTERACT -> ItemInteractActionEvent(stream, player, source, context)
            ItemScriptTrigger.LEFT_CLICK -> ItemLeftClickActionEvent(stream, player, source, context)
            ItemScriptTrigger.RIGHT_CLICK -> ItemRightClickActionEvent(stream, player, source, context)
            ItemScriptTrigger.USE -> ItemUseActionEvent(stream, player, source, context)
            ItemScriptTrigger.RIGHT_CLICK_ENTITY -> ItemInteractEntityActionEvent(stream, player, source, context)
            ItemScriptTrigger.ATTACK -> ItemAttackActionEvent(stream, player, source, context)
            ItemScriptTrigger.DAMAGE -> ItemDamageActionEvent(stream, player, source, context)
            ItemScriptTrigger.BLOCK_BREAK -> ItemBlockBreakActionEvent(stream, player, source, context)
            ItemScriptTrigger.ITEM_BREAK -> ItemItemBreakActionEvent(stream, player, source, context)
            ItemScriptTrigger.CONSUME -> ItemConsumeActionEvent(stream, player, source, context)
            ItemScriptTrigger.PICKUP -> ItemPickupActionEvent(stream, player, source, context)
            ItemScriptTrigger.DROP -> ItemDropActionEvent(stream, player, source, context)
            ItemScriptTrigger.SWAP_TO_MAINHAND -> ItemSwapToMainhandActionEvent(stream, player, source, context)
            ItemScriptTrigger.SWAP_TO_OFFHAND -> ItemSwapToOffhandActionEvent(stream, player, source, context)
            ItemScriptTrigger.INVENTORY_CLICK -> ItemInventoryClickTriggerEvent(stream, player, source, context)
            ItemScriptTrigger.DEATH -> ItemDeathActionEvent(stream, player, source, context)
            ItemScriptTrigger.KILL -> ItemKillActionEvent(stream, player, source, context)
            ItemScriptTrigger.HURT -> ItemHurtActionEvent(stream, player, source, context)
            ItemScriptTrigger.SHOOT -> ItemShootActionEvent(stream, player, source, context)
            ItemScriptTrigger.PROJECTILE_HIT -> ItemProjectileHitActionEvent(stream, player, source, context)
            ItemScriptTrigger.SNEAK -> ItemSneakActionEvent(stream, player, source, context)
            ItemScriptTrigger.SPRINT -> ItemSprintActionEvent(stream, player, source, context)
            ItemScriptTrigger.JUMP -> ItemJumpActionEvent(stream, player, source, context)
            ItemScriptTrigger.RESPAWN -> ItemRespawnActionEvent(stream, player, source, context)
            ItemScriptTrigger.EQUIP -> ItemEquipActionEvent(stream, player, source, context)
            ItemScriptTrigger.UNEQUIP -> ItemUnequipActionEvent(stream, player, source, context)
            else -> ItemActionTriggerEvent(stream, player, source, context, trigger)
        }
    }

    private fun resolveLocale(player: Player): String? {
        return player.localeOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('-', '_')
            ?.lowercase()
    }

    private fun replacePlayerItem(player: Player, source: ItemStack, replacement: ItemStack) {
        // 优先引用比较，回退到 isSimilar 以应对 onAttack 已替换物品的情况
        if (player.inventory.itemInMainHand === source || player.inventory.itemInMainHand.isSimilar(source)) {
            player.inventory.setItemInMainHand(replacement)
            return
        }
        if (player.inventory.itemInOffHand === source || player.inventory.itemInOffHand.isSimilar(source)) {
            player.inventory.setItemInOffHand(replacement)
            return
        }
        val armor = player.inventory.armorContents
        val slot = armor.indexOfFirst { it === source || (it != null && it.isSimilar(source)) }
        if (slot >= 0) {
            armor[slot] = replacement
            player.inventory.armorContents = armor
        }
    }

    /**
     * 从玩家当前背包中查找与 [eventItem] 同类型的物品。
     * 用于 onDamage 等场景，此时 event.item 引用可能已被 onAttack 替换。
     */
    private fun findCurrentItem(player: Player, eventItem: ItemStack): ItemStack? {
        if (player.inventory.itemInMainHand === eventItem || player.inventory.itemInMainHand.isSimilar(eventItem)) {
            return player.inventory.itemInMainHand
        }
        if (player.inventory.itemInOffHand === eventItem || player.inventory.itemInOffHand.isSimilar(eventItem)) {
            return player.inventory.itemInOffHand
        }
        return null
    }

    private fun ensureOwnership(managed: ManagedItem, player: Player?): OwnershipValidation {
        val result = ItemUniqueFeature.checkOwnership(managed.stream, player)
        BaikirutoSettings.debug { info("[Baikiruto][DEBUG][OWNERSHIP] item=${managed.item.id} player=${player?.name} allowed=${result.allowed} changed=${result.changed} owner=${result.owner}") }
        if (!result.allowed) {
            val customMessage = ItemUniqueFeature.customDenyMessage(managed.stream)
            if (customMessage != null) {
                player?.sendMessage(LegacyTextColorizer.colorize(customMessage))
            } else {
                player?.sendLang("item-unique-deny")
            }
            return OwnershipValidation.Denied
        }
        if (result.changed) {
            return OwnershipValidation.Changed
        }
        return OwnershipValidation.Pass
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

