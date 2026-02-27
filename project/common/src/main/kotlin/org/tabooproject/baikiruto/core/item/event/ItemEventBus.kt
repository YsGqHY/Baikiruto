package org.tabooproject.baikiruto.core.item.event

interface ItemEventBus {

    fun post(event: Any)

    fun <T : Any> subscribe(type: Class<T>, handler: (T) -> Unit): AutoCloseable
}
