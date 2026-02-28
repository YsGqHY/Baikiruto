package ink.ptms.zaphkiel

import com.google.gson.JsonObject
import ink.ptms.zaphkiel.api.Display
import ink.ptms.zaphkiel.api.Group
import ink.ptms.zaphkiel.api.Item
import ink.ptms.zaphkiel.api.ItemStream
import ink.ptms.zaphkiel.api.ItemTag
import ink.ptms.zaphkiel.api.Meta
import ink.ptms.zaphkiel.api.Model
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.legacyapi.BaikirutoLegacyAPI
import taboolib.common.platform.function.getDataFolder
import taboolib.library.configuration.ConfigurationSection
import java.io.File

@Deprecated("Use org.tabooproject.baikiruto.core.Baikiruto#api()")
object ZaphkielAPI {

    val loaded: ArrayList<File>
        get() = arrayListOf()

    val folderItem: File
        get() = File(getDataFolder(), "items")

    val folderDisplay: File
        get() = File(getDataFolder(), "display")

    val registeredItem: HashMap<String, Item>
        get() = HashMap(Baikiruto.api().getItemRegistry().entries())

    val registeredModel: HashMap<String, Model>
        get() = HashMap(Baikiruto.api().getModelRegistry().entries())

    val registeredDisplay: HashMap<String, Display>
        get() = HashMap(Baikiruto.api().getDisplayRegistry().entries())

    val registeredGroup: HashMap<String, Group>
        get() = HashMap(Baikiruto.api().getGroupRegistry().entries())

    val registeredMeta: Map<String, Class<*>>
        get() = Baikiruto.api().getMetaFactoryRegistry().entries().mapValues { (_, value) ->
            value::class.java
        }

    fun read(item: ItemStack): ItemStream {
        return requireNotNull(Baikiruto.api().readItem(item)) { "Unable to read managed item stream from ItemStack." }
    }

    fun getItem(id: String, player: Player? = null): ItemStream? {
        return Baikiruto.api().getItemManager().generateItem(
            id,
            linkedMapOf<String, Any?>(
                "player" to player,
                "sender" to player
            )
        )
    }

    fun getItemStack(id: String, player: Player? = null): ItemStack? {
        return getItem(id, player)?.toItemStack()
    }

    fun getName(item: ItemStack): String? {
        return Baikiruto.api().getItemId(item)
    }

    fun getData(item: ItemStack): ItemTag? {
        return Baikiruto.api().getItemData(item)
    }

    fun getUnique(item: ItemStack): ItemTag? {
        return Baikiruto.api().getItemUniqueData(item)
    }

    fun getItem(item: ItemStack): Item? {
        return Baikiruto.api().getItemHandler().getItem(item)
    }

    fun checkUpdate(player: Player?, inventory: Inventory) {
        Baikiruto.api().getItemUpdater().checkUpdate(player, inventory)
    }

    fun checkUpdate(player: Player?, item: ItemStack): ItemStream {
        val updated = Baikiruto.api().getItemUpdater().checkUpdate(player, item)
        return requireNotNull(Baikiruto.api().readItem(updated)) {
            "Unable to read managed item stream from updated ItemStack."
        }
    }

    fun reloadItem() {
        Baikiruto.api().reload()
    }

    fun loadItemFromFile(file: File) {
        Baikiruto.api().getItemLoader().loadItemFromFile(file).forEach {
            Baikiruto.api().getItemManager().registerItem(it)
        }
    }

    fun loadModelFromFile(file: File) {
        Baikiruto.api().getItemLoader().loadModelFromFile(file).forEach {
            Baikiruto.api().getItemManager().registerModel(it)
        }
    }

    fun reloadDisplay() {
        Baikiruto.api().reload()
    }

    fun loadDisplayFromFile(file: File, fromItemFile: Boolean = false) {
        Baikiruto.api().getItemLoader().loadDisplayFromFile(file, fromItemFile).forEach {
            Baikiruto.api().getItemManager().registerDisplay(it)
        }
    }

    fun readMeta(root: ConfigurationSection): MutableList<Meta> {
        return Baikiruto.api().getItemLoader().loadMetaFromSection(root).toMutableList()
    }

    fun serialize(itemStack: ItemStack): JsonObject {
        return BaikirutoLegacyAPI.serializeToJson(itemStack)
    }

    fun serialize(itemStream: ItemStream): JsonObject {
        return BaikirutoLegacyAPI.serializeToJson(itemStream)
    }

    fun deserialize(json: String): ItemStream {
        return Baikiruto.api().getItemSerializer().deserialize(json)
    }

    fun deserialize(json: JsonObject): ItemStream {
        return Baikiruto.api().getItemSerializer().deserialize(json)
    }
}
