package org.tabooproject.baikiruto.impl.item

import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemStream
import taboolib.platform.util.isAir

fun ItemStack?.toBaikirutoStreamOrNull(): ItemStream? {
    if (this == null || this.isAir()) {
        return null
    }
    return Baikiruto.api().readItem(this)
}

fun ItemStack.toBaikirutoStream(): ItemStream {
    return toBaikirutoStreamOrNull() ?: error("This item is not a Baikiruto managed item.")
}
