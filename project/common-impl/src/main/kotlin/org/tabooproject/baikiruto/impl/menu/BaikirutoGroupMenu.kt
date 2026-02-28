package org.tabooproject.baikiruto.impl.menu

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemGroup
import taboolib.common.platform.event.SubscribeEvent

object BaikirutoGroupMenu {

    private const val PAGE_SIZE = 45
    private const val SLOT_PREV = 45
    private const val SLOT_ROOT = 48
    private const val SLOT_BACK = 49
    private const val SLOT_CLOSE = 50
    private const val SLOT_NEXT = 53

    fun open(player: Player, groupId: String? = null, page: Int = 0): Boolean {
        val state = buildState(groupId) ?: return false
        val totalPages = ((state.entries.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        val pageIndex = page.coerceIn(0, totalPages - 1)
        val holder = GroupMenuHolder(
            groupId = state.groupId,
            parentGroupId = state.parentGroupId,
            page = pageIndex,
            totalPages = totalPages
        )
        val title = buildTitle(state.title, pageIndex, totalPages)
        val inventory = Bukkit.createInventory(holder, 54, title)
        holder.backingInventory = inventory
        fillPage(holder, state.entries, pageIndex)
        player.openInventory(inventory)
        return true
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? GroupMenuHolder ?: return
        val topSize = event.view.topInventory.size
        if (event.rawSlot >= topSize) {
            if (event.isShiftClick) {
                event.isCancelled = true
            }
            return
        }
        event.isCancelled = true
        val action = holder.actions[event.rawSlot] ?: return
        when (action) {
            is MenuAction.OpenGroup -> open(player, action.groupId)
            is MenuAction.OpenPage -> open(player, holder.groupId, action.page)
            is MenuAction.OpenParent -> open(player, holder.parentGroupId)
            is MenuAction.OpenRoot -> open(player, null)
            is MenuAction.GiveItem -> {
                val amount = if (event.isShiftClick) 64 else 1
                val success = Baikiruto.api().getItemManager().giveItem(
                    player = player,
                    itemId = action.itemId,
                    amount = amount,
                    context = mapOf(
                        "sender" to player,
                        "player" to player,
                        "source" to "group-menu"
                    )
                )
                if (!success) {
                    player.sendMessage(color("&cGive cancelled for &f${action.itemId}&c."))
                } else {
                    player.sendMessage(color("&aGiven &f$amount&a x &f${action.itemId}&a."))
                }
            }
            MenuAction.Close -> player.closeInventory()
        }
    }

    @SubscribeEvent(ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? GroupMenuHolder ?: return
        if (holder.totalPages <= 0) {
            return
        }
        val topSize = event.view.topInventory.size
        if (event.rawSlots.any { it < topSize }) {
            event.isCancelled = true
        }
    }

    private fun fillPage(holder: GroupMenuHolder, entries: List<MenuEntry>, page: Int) {
        holder.actions.clear()
        val start = page * PAGE_SIZE
        entries.drop(start).take(PAGE_SIZE).forEachIndexed { index, entry ->
            when (entry) {
                is MenuEntry.GroupEntry -> {
                    holder.backingInventory.setItem(index, renderGroupIcon(entry.group))
                    holder.actions[index] = MenuAction.OpenGroup(entry.group.id)
                }
                is MenuEntry.ItemEntry -> {
                    holder.backingInventory.setItem(index, renderItemIcon(entry.item))
                    holder.actions[index] = MenuAction.GiveItem(entry.item.id)
                }
            }
        }
        if (page > 0) {
            holder.backingInventory.setItem(SLOT_PREV, controlIcon(Material.ARROW, "&ePrevious", listOf("&7Open previous page")))
            holder.actions[SLOT_PREV] = MenuAction.OpenPage(page - 1)
        }
        if (page < holder.totalPages - 1) {
            holder.backingInventory.setItem(SLOT_NEXT, controlIcon(Material.ARROW, "&eNext", listOf("&7Open next page")))
            holder.actions[SLOT_NEXT] = MenuAction.OpenPage(page + 1)
        }
        holder.backingInventory.setItem(SLOT_CLOSE, controlIcon(Material.BARRIER, "&cClose", listOf("&7Close this menu")))
        holder.actions[SLOT_CLOSE] = MenuAction.Close
        holder.backingInventory.setItem(SLOT_ROOT, controlIcon(Material.COMPASS, "&6Root", listOf("&7Back to root groups")))
        holder.actions[SLOT_ROOT] = MenuAction.OpenRoot
        if (!holder.parentGroupId.isNullOrBlank()) {
            holder.backingInventory.setItem(SLOT_BACK, controlIcon(Material.CHEST, "&eParent", listOf("&7Back to parent group")))
            holder.actions[SLOT_BACK] = MenuAction.OpenParent
        }
    }

    private fun buildState(groupId: String?): MenuState? {
        val api = Baikiruto.api()
        val groups = api.getGroupRegistry().values()
            .sortedWith(compareByDescending<ItemGroup> { it.priority }.thenBy { it.path }.thenBy { it.id })
        val items = api.getItemRegistry().values().sortedBy { it.id }
        val normalizedGroupId = groupId?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedGroupId.isNullOrBlank()) {
            val rootGroups = groups.filter { it.parentId.isNullOrBlank() }
            val rootItems = items.filter { it.groupId.isNullOrBlank() }
            return MenuState(
                groupId = null,
                parentGroupId = null,
                title = "Root",
                entries = buildEntries(rootGroups, rootItems)
            )
        }
        val currentGroup = groups.firstOrNull { equalsId(it.id, normalizedGroupId) } ?: return null
        val childGroups = groups.filter { equalsId(it.parentId, currentGroup.id) }
        val groupedItems = items.filter { equalsId(it.groupId, currentGroup.id) }
        return MenuState(
            groupId = currentGroup.id,
            parentGroupId = currentGroup.parentId,
            title = currentGroup.path.ifBlank { currentGroup.id },
            entries = buildEntries(childGroups, groupedItems)
        )
    }

    private fun buildEntries(groups: List<ItemGroup>, items: List<Item>): List<MenuEntry> {
        val entries = arrayListOf<MenuEntry>()
        groups.forEach { group ->
            entries += MenuEntry.GroupEntry(group)
        }
        items.forEach { item ->
            entries += MenuEntry.ItemEntry(item)
        }
        return entries
    }

    private fun renderGroupIcon(group: ItemGroup): ItemStack {
        val icon = resolveIcon(group.icon, Material.CHEST)
        val managedItems = Baikiruto.api().getItemRegistry().values().count { equalsId(it.groupId, group.id) }
        val childGroups = Baikiruto.api().getGroupRegistry().values().count { equalsId(it.parentId, group.id) }
        return decorate(
            icon,
            "&6Group: &f${group.id}",
            listOf(
                "&7Path: &f${group.path}",
                "&7Child groups: &f$childGroups",
                "&7Managed items: &f$managedItems",
                "&8Click to open"
            )
        )
    }

    private fun renderItemIcon(item: Item): ItemStack {
        val preview = Baikiruto.api().buildItem(
            itemId = item.id,
            context = mapOf(
                "menu" to true,
                "debug" to false
            )
        ) ?: ItemStack(Material.PAPER)
        val display = preview.itemMeta?.displayName?.takeIf { it.isNotBlank() } ?: "&f${item.id}"
        return decorate(
            preview,
            display,
            listOf(
                "&7ID: &f${item.id}",
                "&7Group: &f${item.groupId ?: "root"}",
                "&eLeft click: give 1",
                "&eShift+left: give 64"
            )
        )
    }

    private fun resolveIcon(source: String?, fallback: Material): ItemStack {
        val trimmed = source?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            Baikiruto.api().buildItem(trimmed, mapOf("menu" to true, "debug" to false))?.let {
                return it
            }
            val materialName = trimmed.substringAfter(':')
            Material.matchMaterial(materialName.uppercase())?.let {
                return ItemStack(it)
            }
            Material.matchMaterial(trimmed.uppercase())?.let {
                return ItemStack(it)
            }
        }
        return ItemStack(fallback)
    }

    private fun controlIcon(material: Material, name: String, lore: List<String>): ItemStack {
        return decorate(ItemStack(material), name, lore)
    }

    private fun decorate(source: ItemStack, name: String, lore: List<String>): ItemStack {
        val item = source.clone().apply { amount = 1 }
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(color(name))
        meta.lore = lore.map(::color)
        item.itemMeta = meta
        return item
    }

    private fun buildTitle(group: String, page: Int, totalPages: Int): String {
        val raw = "&6Baikiruto &7| &f$group &8(${page + 1}/$totalPages)"
        return color(trimTitle(raw))
    }

    private fun trimTitle(title: String): String {
        return if (title.length <= 32) title else title.take(32)
    }

    private fun color(text: String): String {
        return ChatColor.translateAlternateColorCodes('&', text)
    }

    private fun equalsId(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) {
            return false
        }
        return left.equals(right, ignoreCase = true)
    }

    private sealed class MenuEntry {
        data class GroupEntry(val group: ItemGroup) : MenuEntry()
        data class ItemEntry(val item: Item) : MenuEntry()
    }

    private sealed class MenuAction {
        data class OpenGroup(val groupId: String) : MenuAction()
        data class OpenPage(val page: Int) : MenuAction()
        object OpenParent : MenuAction()
        object OpenRoot : MenuAction()
        data class GiveItem(val itemId: String) : MenuAction()
        object Close : MenuAction()
    }

    private data class MenuState(
        val groupId: String?,
        val parentGroupId: String?,
        val title: String,
        val entries: List<MenuEntry>
    )

    private class GroupMenuHolder(
        val groupId: String?,
        val parentGroupId: String?,
        val page: Int,
        val totalPages: Int
    ) : InventoryHolder {

        lateinit var backingInventory: Inventory
        val actions = hashMapOf<Int, MenuAction>()

        override fun getInventory(): Inventory {
            return backingInventory
        }
    }
}
