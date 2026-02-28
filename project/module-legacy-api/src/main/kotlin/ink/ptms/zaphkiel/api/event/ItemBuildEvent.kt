package ink.ptms.zaphkiel.api.event

import ink.ptms.zaphkiel.api.ItemStream
import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.platform.type.BukkitProxyEvent

class ItemBuildEvent {

    class Pre(
        val player: Player?,
        val itemStream: ItemStream,
        val name: MutableMap<String, String>,
        val lore: MutableMap<String, MutableList<String>>
    ) : BukkitProxyEvent(), Editable {

        val item = Baikiruto.api().getItem(itemStream.itemId)

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

    class Post(
        val player: Player?,
        val itemStream: ItemStream,
        val name: Map<String, String>,
        val lore: Map<String, MutableList<String>>
    ) : BukkitProxyEvent() {

        override val allowCancelled: Boolean
            get() = false

        val item = Baikiruto.api().getItem(itemStream.itemId)
    }

    class CheckUpdate(
        val player: Player?,
        val itemStream: ItemStream,
        isOutdated: Boolean
    ) : BukkitProxyEvent() {

        val item = Baikiruto.api().getItem(itemStream.itemId)

        init {
            isCancelled = !isOutdated
        }
    }
}
