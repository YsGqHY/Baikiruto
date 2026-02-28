package ink.ptms.zaphkiel.api.event

import ink.ptms.zaphkiel.api.Display as ZapDisplay
import ink.ptms.zaphkiel.api.ItemStream
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.platform.type.BukkitProxyEvent

class ItemReleaseEvent(
    var icon: Material,
    var data: Int,
    var itemMeta: ItemMeta,
    val itemStream: ItemStream,
    val player: Player? = null
) : BukkitProxyEvent() {

    override val allowCancelled: Boolean
        get() = false

    val item = Baikiruto.api().getItem(itemStream.itemId)

    class Final(
        var itemStack: ItemStack,
        val itemStream: ItemStream,
        val player: Player? = null
    ) : BukkitProxyEvent() {

        override val allowCancelled: Boolean
            get() = false

        val item = Baikiruto.api().getItem(itemStream.itemId)
    }

    class Display(
        val itemStream: ItemStream,
        val name: MutableMap<String, String>,
        val lore: MutableMap<String, MutableList<String>>,
        val player: Player? = null
    ) : BukkitProxyEvent(), Editable {

        val item = Baikiruto.api().getItem(itemStream.itemId)

        override val allowCancelled: Boolean
            get() = false

        override fun addName(key: String, value: Any) {
            name[key] = value.toString()
        }

        override fun addLore(key: String, value: Any) {
            val list = lore.computeIfAbsent(key) { arrayListOf() }
            when (value) {
                is List<*> -> list.addAll(value.map { it.toString() })
                else -> list.add(value.toString())
            }
        }

        override fun addLore(key: String, value: List<Any>) {
            value.forEach { addLore(key, it) }
        }
    }

    class SelectDisplay(
        val itemStream: ItemStream,
        var display: ZapDisplay?,
        val player: Player? = null
    ) : BukkitProxyEvent() {

        val item = Baikiruto.api().getItem(itemStream.itemId)

        override val allowCancelled: Boolean
            get() = false
    }
}
