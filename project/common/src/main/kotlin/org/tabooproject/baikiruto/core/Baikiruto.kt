package org.tabooproject.baikiruto.core

/**
 * Baikiruto
 * org.tabooproject.baikiruto.core.Baikiruto
 *
 * @author mical
 * @since 2026/2/26 23:02
 */
object Baikiruto {

    private var api: BaikirutoAPI? = null

    fun apiOrNull(): BaikirutoAPI? {
        return api
    }

    fun isRegistered(): Boolean {
        return api != null
    }

    fun api(): BaikirutoAPI {
        return apiOrNull() ?: error("BaikirutoAPI has not finished loading, or failed to load!")
    }

    fun register(api: BaikirutoAPI) {
        Baikiruto.api = api
    }
}