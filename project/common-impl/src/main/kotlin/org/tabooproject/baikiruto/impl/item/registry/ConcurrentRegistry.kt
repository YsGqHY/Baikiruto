package org.tabooproject.baikiruto.impl.item.registry

import org.tabooproject.baikiruto.core.item.Registry
import java.util.concurrent.ConcurrentHashMap

class ConcurrentRegistry<T> : Registry<T> {

    private val registryValues = ConcurrentHashMap<String, T>()

    override fun register(id: String, value: T): T {
        registryValues[normalize(id)] = value
        return value
    }

    override fun unregister(id: String): T? {
        return registryValues.remove(normalize(id))
    }

    override fun get(id: String): T? {
        return registryValues[normalize(id)]
    }

    override fun contains(id: String): Boolean {
        return registryValues.containsKey(normalize(id))
    }

    override fun keys(): Set<String> {
        return registryValues.keys
    }

    override fun values(): Collection<T> {
        return registryValues.values
    }

    override fun entries(): Map<String, T> {
        return registryValues.toMap()
    }

    override fun clear() {
        registryValues.clear()
    }

    private fun normalize(id: String): String {
        return id.trim().lowercase()
    }
}
