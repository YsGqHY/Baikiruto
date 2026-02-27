package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.SerializedItem

data class DefaultSerializedItem(
    override val itemId: String,
    override val amount: Int,
    override val versionHash: String,
    override val metaHistory: List<String>,
    override val runtimeData: Map<String, Any?>,
    override val itemStackData: String
) : SerializedItem
