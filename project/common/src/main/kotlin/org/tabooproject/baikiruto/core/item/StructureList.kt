package org.tabooproject.baikiruto.core.item

interface StructureList {

    fun build(vars: Map<String, List<String>>, trim: Boolean = true): List<String>
}
