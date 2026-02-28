package org.tabooproject.baikiruto.core.item

data class ItemDisplay(
    val id: String,
    val name: Map<String, String> = emptyMap(),
    val lore: Map<String, List<String>> = emptyMap(),
    val data: Map<String, Any?> = emptyMap()
) {

    val structureName: StructureSingle? = resolveNameTemplate()?.let(::DefaultStructureSingle)

    val structureLore: StructureList = DefaultStructureList(resolveLoreTemplate())

    fun build(
        name: Map<String, String>,
        lore: Map<String, List<String>>,
        trim: Boolean = true
    ): DisplayProduct {
        return DisplayProduct(
            name = structureName?.build(name, trim),
            lore = structureLore.build(lore, trim)
        )
    }

    fun resolveDisplayName(): String? {
        return structureName?.build(name, trim = true)
            ?.takeIf { it.isNotEmpty() }
    }

    fun resolveLore(): List<String> {
        return structureLore.build(lore, trim = true)
    }

    private fun resolveNameTemplate(): String? {
        return name["item_name"] ?: name.entries.firstOrNull()?.value
    }

    private fun resolveLoreTemplate(): List<String> {
        return lore.entries.sortedBy { it.key }.flatMap { it.value }
    }
}
