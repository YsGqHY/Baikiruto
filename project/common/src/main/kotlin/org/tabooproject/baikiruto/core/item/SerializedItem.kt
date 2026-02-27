package org.tabooproject.baikiruto.core.item

interface SerializedItem {

    val itemId: String

    val amount: Int

    val versionHash: String

    val metaHistory: List<String>

    val runtimeData: Map<String, Any?>

    val itemStackData: String
}
