package org.tabooproject.baikiruto.core.item

data class ItemGroup(
    val id: String,
    val path: String,
    val parentId: String? = null,
    val priority: Int = 0,
    val icon: String? = null
)
