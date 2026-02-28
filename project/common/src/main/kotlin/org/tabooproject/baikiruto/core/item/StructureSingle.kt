package org.tabooproject.baikiruto.core.item

interface StructureSingle {

    fun build(vars: Map<String, String>, trim: Boolean = true): String
}
