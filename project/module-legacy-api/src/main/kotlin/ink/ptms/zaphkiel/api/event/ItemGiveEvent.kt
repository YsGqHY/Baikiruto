package ink.ptms.zaphkiel.api.event

import ink.ptms.zaphkiel.api.ItemStream
import org.bukkit.entity.Player
import taboolib.platform.type.BukkitProxyEvent

class ItemGiveEvent(val player: Player, var itemStream: ItemStream, var amount: Int) : BukkitProxyEvent()
