package org.tabooproject.baikiruto.core.item

data class ItemModel(
    val id: String,
    val data: Map<String, Any?> = emptyMap()
)
