package org.tabooproject.baikiruto.core.item

interface Registry<T> {

    fun register(id: String, value: T): T

    fun unregister(id: String): T?

    fun get(id: String): T?

    fun contains(id: String): Boolean

    fun keys(): Set<String>

    fun values(): Collection<T>

    fun entries(): Map<String, T>

    fun clear()
}
