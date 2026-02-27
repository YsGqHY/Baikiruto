package org.tabooproject.baikiruto.core.item

data class ItemDisplay(
    val id: String,
    val name: Map<String, String> = emptyMap(),
    val lore: Map<String, List<String>> = emptyMap(),
    val data: Map<String, Any?> = emptyMap()
) {

    fun resolveDisplayName(): String? {
        return name["item_name"] ?: name.entries.firstOrNull()?.value
    }

    fun resolveLore(): List<String> {
        return lore.entries
            .sortedBy { it.key }
            .flatMap { it.value }
    }
}
