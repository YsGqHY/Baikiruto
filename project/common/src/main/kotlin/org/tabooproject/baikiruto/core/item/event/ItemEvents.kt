package org.tabooproject.baikiruto.core.item.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.Material
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream

open class ItemLifecycleEvent(
    var stream: ItemStream,
    val player: Player?,
    val source: Any?,
    val context: MutableMap<String, Any?> = linkedMapOf()
) {

    var cancelled: Boolean = false

    val itemId: String
        get() = stream.itemId
}

class ItemBuildPreEvent(
    stream: ItemStream,
    player: Player?,
    context: MutableMap<String, Any?>,
    val name: MutableMap<String, String> = linkedMapOf(),
    val lore: MutableMap<String, MutableList<String>> = linkedMapOf()
) : ItemLifecycleEvent(stream, player, null, context) {

    val itemStream: ItemStream
        get() = stream

    fun addName(key: String, value: Any?) {
        val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return
        name[normalizedKey] = value?.toString().orEmpty()
    }

    fun addLore(key: String, value: Any?) {
        val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return
        val values = lore.computeIfAbsent(normalizedKey) { arrayListOf() }
        when (value) {
            null -> values += "null"
            is Iterable<*> -> value.forEach { values += it?.toString().orEmpty() }
            else -> values += value.toString()
        }
    }

    fun addLore(key: String, values: List<Any?>) {
        values.forEach { addLore(key, it) }
    }
}

class ItemBuildPostEvent(
    stream: ItemStream,
    player: Player?,
    context: MutableMap<String, Any?>,
    val name: Map<String, String> = emptyMap(),
    val lore: Map<String, MutableList<String>> = emptyMap()
) : ItemLifecycleEvent(stream, player, null, context) {

    val itemStream: ItemStream
        get() = stream
}

class ItemReleaseEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>,
    var icon: Material,
    var data: Int,
    var itemMeta: ItemMeta?
) : ItemLifecycleEvent(stream, player, source, context) {
}

class ItemReleaseDisplayEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemLifecycleEvent(stream, player, source, context) {
}

class ItemReleaseFinalEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>,
    var itemStack: ItemStack
) : ItemLifecycleEvent(stream, player, source, context) {
}

class ItemGiveEvent(
    stream: ItemStream,
    player: Player,
    var amount: Int
) : ItemLifecycleEvent(stream, player, null)

class ItemUpdateEvent(
    stream: ItemStream,
    player: Player?,
    val oldVersionHash: String,
    val newVersionHash: String
) : ItemLifecycleEvent(stream, player, null)

class ItemCheckUpdateEvent(
    stream: ItemStream,
    player: Player?,
    val oldVersionHash: String,
    val latestVersionHash: String,
    var isOutdated: Boolean
) : ItemLifecycleEvent(stream, player, null) {

    init {
        cancelled = !isOutdated
    }

    val itemStream: ItemStream
        get() = stream
}

class ItemSelectDisplayEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>,
    var displayId: String?,
    var display: ItemDisplay?
) : ItemLifecycleEvent(stream, player, source, context)

class ItemReleaseDisplayBuildEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>,
    var displayId: String?,
    var display: ItemDisplay?,
    val name: MutableMap<String, String>,
    val lore: MutableMap<String, MutableList<String>>
) : ItemLifecycleEvent(stream, player, source, context) {

    fun addName(key: String, value: Any?) {
        val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return
        name[normalizedKey] = value?.toString().orEmpty()
    }

    fun addLore(key: String, value: Any?) {
        val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return
        val values = lore.computeIfAbsent(normalizedKey) { arrayListOf() }
        when (value) {
            null -> values += "null"
            is Iterable<*> -> value.forEach { values += it?.toString().orEmpty() }
            else -> values += value.toString()
        }
    }

    fun addLore(key: String, values: List<Any?>) {
        values.forEach { addLore(key, it) }
    }
}

open class ItemActionTriggerEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>,
    val trigger: ItemScriptTrigger
) : ItemLifecycleEvent(stream, player, source, context) {

    var save: Boolean = false

    val itemStream: ItemStream
        get() = stream

    val cancellable: Cancellable?
        get() = source as? Cancellable

    fun cancel() {
        cancelled = true
        cancellable?.isCancelled = true
    }

    inline fun <reified T : Any> sourceAs(): T? {
        return source as? T
    }

    fun <T : Any> sourceAs(type: Class<T>): T? {
        if (!type.isInstance(source)) {
            return null
        }
        return type.cast(source)
    }
}

class ItemSelectActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.SELECT) {

    val heldEvent: PlayerItemHeldEvent?
        get() = source as? PlayerItemHeldEvent

    val previousSlot: Int
        get() = heldEvent?.previousSlot ?: -1

    val newSlot: Int
        get() = heldEvent?.newSlot ?: -1
}

class ItemAsyncTickActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.ASYNC_TICK)

class ItemInteractActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.INTERACT) {

    val bukkitEvent: PlayerInteractEvent?
        get() = source as? PlayerInteractEvent

    val action: Action?
        get() = bukkitEvent?.action

    val hand: EquipmentSlot?
        get() = bukkitEvent?.hand

    val item: ItemStack?
        get() = bukkitEvent?.item

    val material
        get() = bukkitEvent?.material

    val isBlockInHand: Boolean
        get() = bukkitEvent?.isBlockInHand == true

    val clickedBlock
        get() = bukkitEvent?.clickedBlock

    val blockFace
        get() = bukkitEvent?.blockFace

    fun hasBlock(): Boolean {
        return bukkitEvent?.hasBlock() == true
    }

    fun hasItem(): Boolean {
        return bukkitEvent?.hasItem() == true
    }

    fun isRightClick(): Boolean {
        val eventAction = action ?: return false
        return eventAction == Action.RIGHT_CLICK_AIR || eventAction == Action.RIGHT_CLICK_BLOCK
    }

    fun isLeftClick(): Boolean {
        val eventAction = action ?: return false
        return eventAction == Action.LEFT_CLICK_AIR || eventAction == Action.LEFT_CLICK_BLOCK
    }

    fun isRightClickAir(): Boolean {
        return action == Action.RIGHT_CLICK_AIR
    }

    fun isRightClickBlock(): Boolean {
        return action == Action.RIGHT_CLICK_BLOCK
    }

    fun isLeftClickAir(): Boolean {
        return action == Action.LEFT_CLICK_AIR
    }

    fun isLeftClickBlock(): Boolean {
        return action == Action.LEFT_CLICK_BLOCK
    }

    fun isPhysical(): Boolean {
        return action == Action.PHYSICAL
    }

    fun isMainhand(): Boolean {
        return hand == EquipmentSlot.HAND
    }

    fun isOffhand(): Boolean {
        return hand == EquipmentSlot.OFF_HAND
    }
}

class ItemLeftClickActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.LEFT_CLICK) {

    val bukkitEvent: PlayerInteractEvent?
        get() = source as? PlayerInteractEvent
}

class ItemRightClickActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.RIGHT_CLICK) {

    val bukkitEvent: PlayerInteractEvent?
        get() = source as? PlayerInteractEvent
}

class ItemUseActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.USE) {

    val interactEvent: PlayerInteractEvent?
        get() = source as? PlayerInteractEvent

    val consumeEvent: PlayerItemConsumeEvent?
        get() = source as? PlayerItemConsumeEvent
}

class ItemInteractEntityActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.RIGHT_CLICK_ENTITY) {

    val bukkitEvent: PlayerInteractEntityEvent?
        get() = source as? PlayerInteractEntityEvent

    val hand: EquipmentSlot?
        get() = bukkitEvent?.hand

    val rightClicked
        get() = bukkitEvent?.rightClicked

    fun isMainHand(): Boolean {
        return hand == EquipmentSlot.HAND
    }

    fun isMainhand(): Boolean {
        return isMainHand()
    }

    fun isOffhand(): Boolean {
        return hand == EquipmentSlot.OFF_HAND
    }
}

class ItemAttackActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.ATTACK) {

    val bukkitEvent: EntityDamageByEntityEvent?
        get() = source as? EntityDamageByEntityEvent
}

class ItemDamageActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.DAMAGE) {

    val bukkitEvent: PlayerItemDamageEvent?
        get() = source as? PlayerItemDamageEvent

    var damage: Int
        get() = bukkitEvent?.damage ?: 0
        set(value) {
            bukkitEvent?.damage = value
        }
}

class ItemBlockBreakActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.BLOCK_BREAK) {

    val bukkitEvent: BlockBreakEvent?
        get() = source as? BlockBreakEvent

    val block
        get() = bukkitEvent?.block
}

class ItemItemBreakActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.ITEM_BREAK) {

    val bukkitEvent: PlayerItemBreakEvent?
        get() = source as? PlayerItemBreakEvent

    val brokenItem: ItemStack?
        get() = bukkitEvent?.brokenItem
}

class ItemConsumeActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.CONSUME) {

    val bukkitEvent: PlayerItemConsumeEvent?
        get() = source as? PlayerItemConsumeEvent

    var consumedItem: ItemStack?
        get() = bukkitEvent?.item
        set(value) {
            bukkitEvent?.setItem(value)
        }

    var item: ItemStack?
        get() = consumedItem
        set(value) {
            consumedItem = value
        }

    val consumer: Player?
        get() = bukkitEvent?.player
}

class ItemPickupActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.PICKUP) {

    val bukkitEvent: EntityPickupItemEvent?
        get() = source as? EntityPickupItemEvent

    val itemEntity
        get() = bukkitEvent?.item

    val remaining: Int
        get() = bukkitEvent?.remaining ?: 0

    var pickedItem: ItemStack?
        get() = bukkitEvent?.item?.itemStack
        set(value) {
            if (value != null) {
                bukkitEvent?.item?.itemStack = value
            }
        }

    var item: ItemStack?
        get() = pickedItem
        set(value) {
            pickedItem = value
        }
}

class ItemDropActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.DROP) {

    val bukkitEvent: PlayerDropItemEvent?
        get() = source as? PlayerDropItemEvent

    val itemDrop
        get() = bukkitEvent?.itemDrop

    var droppedItem: ItemStack?
        get() = bukkitEvent?.itemDrop?.itemStack
        set(value) {
            if (value != null) {
                bukkitEvent?.itemDrop?.itemStack = value
            }
        }

    var item: ItemStack?
        get() = droppedItem
        set(value) {
            droppedItem = value
        }
}

class ItemSwapToMainhandActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.SWAP_TO_MAINHAND) {

    val bukkitEvent: PlayerSwapHandItemsEvent?
        get() = source as? PlayerSwapHandItemsEvent

    var mainHandItem: ItemStack?
        get() = bukkitEvent?.mainHandItem
        set(value) {
            bukkitEvent?.mainHandItem = value
        }
}

class ItemSwapToOffhandActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.SWAP_TO_OFFHAND) {

    val bukkitEvent: PlayerSwapHandItemsEvent?
        get() = source as? PlayerSwapHandItemsEvent

    var offHandItem: ItemStack?
        get() = bukkitEvent?.offHandItem
        set(value) {
            bukkitEvent?.offHandItem = value
        }
}

class ItemInventoryClickTriggerEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.INVENTORY_CLICK) {

    val bukkitEvent: InventoryClickEvent?
        get() = source as? InventoryClickEvent

    val click: ClickType?
        get() = bukkitEvent?.click

    val action: InventoryAction?
        get() = bukkitEvent?.action

    val slot: Int
        get() = bukkitEvent?.slot ?: -1

    val rawSlot: Int
        get() = bukkitEvent?.rawSlot ?: -1

    val hotbarButton: Int
        get() = bukkitEvent?.hotbarButton ?: -1

    val slotType: InventoryType.SlotType?
        get() = bukkitEvent?.slotType

    val clickedInventory: Inventory?
        get() = bukkitEvent?.clickedInventory

    var cursor: ItemStack?
        get() = bukkitEvent?.cursor
        set(value) {
            bukkitEvent?.whoClicked?.setItemOnCursor(value)
        }

    var currentItem: ItemStack?
        get() = bukkitEvent?.currentItem
        set(value) {
            bukkitEvent?.currentItem = value
        }

    val isShiftClick: Boolean
        get() = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT

    val isLeftClick: Boolean
        get() = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT

    val isRightClick: Boolean
        get() = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT
}

class ItemInventoryClickActionEvent(
    val currentStream: ItemStream?,
    val buttonStream: ItemStream?,
    val player: Player,
    val source: Any?,
    val context: MutableMap<String, Any?> = linkedMapOf()
) {

    var cancelled: Boolean = false

    var saveCurrent: Boolean = false

    var saveButton: Boolean = false

    val itemStreamCurrent: ItemStream?
        get() = currentStream

    val itemStreamButton: ItemStream?
        get() = buttonStream

    val cancellable: Cancellable?
        get() = source as? Cancellable

    val bukkitEvent: InventoryClickEvent?
        get() = source as? InventoryClickEvent

    val click: ClickType?
        get() = bukkitEvent?.click

    val action: InventoryAction?
        get() = bukkitEvent?.action

    val slot: Int
        get() = bukkitEvent?.slot ?: -1

    val rawSlot: Int
        get() = bukkitEvent?.rawSlot ?: -1

    val hotbarButton: Int
        get() = bukkitEvent?.hotbarButton ?: -1

    val slotType: InventoryType.SlotType?
        get() = bukkitEvent?.slotType

    val clickedInventory: Inventory?
        get() = bukkitEvent?.clickedInventory

    var cursorItem: ItemStack?
        get() = bukkitEvent?.cursor
        set(value) {
            bukkitEvent?.whoClicked?.setItemOnCursor(value)
        }

    var cursor: ItemStack?
        get() = cursorItem
        set(value) {
            cursorItem = value
        }

    var currentItem: ItemStack?
        get() = bukkitEvent?.currentItem
        set(value) {
            bukkitEvent?.currentItem = value
        }

    val whoClicked
        get() = bukkitEvent?.whoClicked

    val isShiftClick: Boolean
        get() = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT

    val isLeftClick: Boolean
        get() = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT

    val isRightClick: Boolean
        get() = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT

    val isLeftClickStrict: Boolean
        get() = click == ClickType.LEFT

    val isRightClickStrict: Boolean
        get() = click == ClickType.RIGHT

    fun cancel() {
        cancelled = true
        cancellable?.isCancelled = true
    }
}

// ── 新增触发器事件类 ──

class ItemDeathActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.DEATH) {

    val bukkitEvent: PlayerDeathEvent?
        get() = source as? PlayerDeathEvent

    val killer: Player?
        get() = bukkitEvent?.entity?.killer

    val deathMessage: String?
        get() = bukkitEvent?.deathMessage

    val drops: MutableList<ItemStack>?
        get() = bukkitEvent?.drops

    val slot: String?
        get() = context["slot"] as? String
}

class ItemKillActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.KILL) {

    val bukkitEvent: EntityDeathEvent?
        get() = source as? EntityDeathEvent

    val killed: org.bukkit.entity.LivingEntity?
        get() = bukkitEvent?.entity

    val droppedExp: Int
        get() = bukkitEvent?.droppedExp ?: 0

    val drops: MutableList<ItemStack>?
        get() = bukkitEvent?.drops
}

class ItemHurtActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.HURT) {

    val bukkitEvent: EntityDamageEvent?
        get() = source as? EntityDamageEvent

    val cause: EntityDamageEvent.DamageCause?
        get() = bukkitEvent?.cause

    val damage: Double
        get() = bukkitEvent?.damage ?: 0.0

    val finalDamage: Double
        get() = bukkitEvent?.finalDamage ?: 0.0

    val attacker: org.bukkit.entity.Entity?
        get() = (bukkitEvent as? EntityDamageByEntityEvent)?.damager

    val slot: String?
        get() = context["slot"] as? String
}

class ItemShootActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.SHOOT) {

    val bukkitEvent: ProjectileLaunchEvent?
        get() = source as? ProjectileLaunchEvent

    val projectile: org.bukkit.entity.Projectile?
        get() = bukkitEvent?.entity
}

class ItemProjectileHitActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.PROJECTILE_HIT) {

    val bukkitEvent: ProjectileHitEvent?
        get() = source as? ProjectileHitEvent

    val projectile: org.bukkit.entity.Projectile?
        get() = bukkitEvent?.entity

    val hitEntity: org.bukkit.entity.Entity?
        get() = bukkitEvent?.hitEntity

    val hitBlock: org.bukkit.block.Block?
        get() = bukkitEvent?.hitBlock
}

class ItemSneakActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.SNEAK) {

    val bukkitEvent: PlayerToggleSneakEvent?
        get() = source as? PlayerToggleSneakEvent

    val isSneaking: Boolean
        get() = bukkitEvent?.isSneaking == true

    val slot: String?
        get() = context["slot"] as? String
}

class ItemSprintActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.SPRINT) {

    val bukkitEvent: PlayerToggleSprintEvent?
        get() = source as? PlayerToggleSprintEvent

    val isSprinting: Boolean
        get() = bukkitEvent?.isSprinting == true

    val slot: String?
        get() = context["slot"] as? String
}

class ItemJumpActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.JUMP) {

    val slot: String?
        get() = context["slot"] as? String
}

class ItemRespawnActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.RESPAWN) {

    val bukkitEvent: PlayerRespawnEvent?
        get() = source as? PlayerRespawnEvent

    val respawnLocation: org.bukkit.Location?
        get() = bukkitEvent?.respawnLocation

    val isBedSpawn: Boolean
        get() = bukkitEvent?.isBedSpawn == true

    val slot: String?
        get() = context["slot"] as? String
}

class ItemEquipActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.EQUIP) {

    val slot: String?
        get() = context["slot"] as? String
}

class ItemUnequipActionEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemActionTriggerEvent(stream, player, source, context, ItemScriptTrigger.UNEQUIP) {

    val slot: String?
        get() = context["slot"] as? String
}
