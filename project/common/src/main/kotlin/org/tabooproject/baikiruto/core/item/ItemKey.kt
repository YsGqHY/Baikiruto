package org.tabooproject.baikiruto.core.item

enum class ItemKey(val key: String) {

    ID("a"),
    VERSION("b"),
    DATA("c"),
    UNIQUE("d"),
    META_HISTORY("e"),
    ROOT("baikiruto");

    override fun toString(): String {
        return key
    }
}
