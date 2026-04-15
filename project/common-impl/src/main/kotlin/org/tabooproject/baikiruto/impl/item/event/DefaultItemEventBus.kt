package org.tabooproject.baikiruto.impl.item.event

import org.tabooproject.baikiruto.core.item.event.ItemEventBus
import taboolib.platform.type.BukkitProxyEvent
import java.util.concurrent.CopyOnWriteArrayList

object DefaultItemEventBus : ItemEventBus {

    private val subscribers = CopyOnWriteArrayList<Subscriber<*>>()

    override fun post(event: Any) {
        subscribers.forEach { subscriber ->
            if (!subscriber.type.isInstance(event)) {
                return@forEach
            }
            try {
                @Suppress("UNCHECKED_CAST")
                (subscriber as Subscriber<Any>).handler(event)
            } catch (_: Exception) {
                // ignore subscriber failure to keep event bus isolated
            }
        }
        if (event is BukkitProxyEvent) {
            try {
                event.call()
            } catch (_: Exception) {
                // ignore proxy event dispatch failure to keep event bus isolated
            }
        }
    }

    override fun <T : Any> subscribe(type: Class<T>, handler: (T) -> Unit): AutoCloseable {
        val subscriber = Subscriber(type, handler)
        subscribers += subscriber
        return AutoCloseable {
            subscribers.remove(subscriber)
        }
    }

    private data class Subscriber<T : Any>(
        val type: Class<T>,
        val handler: (T) -> Unit
    )
}
