package org.tabooproject.baikiruto.core.item

data class ItemStreamData(
    val itemId: String,
    val versionHash: String,
    val metaHistory: List<String>,
    val runtimeData: Map<String, Any?>
)
