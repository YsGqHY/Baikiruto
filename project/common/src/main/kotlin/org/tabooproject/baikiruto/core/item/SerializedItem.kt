package org.tabooproject.baikiruto.core.item

interface SerializedItem : JsonContainer {

    val itemId: String

    val amount: Int

    val versionHash: String

    val metaHistory: List<String>

    val runtimeData: Map<String, Any?>

    val itemStackData: String

    val id: String
        get() = itemId

    val data: Map<String, Any?>?
        get() = runtimeData.ifEmpty { null }

    val uniqueData: UniqueData?
        get() {
            val uuid = runtimeData["unique.uuid"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val date = when (val raw = runtimeData["unique.date"]) {
                is Number -> raw.toLong()
                is String -> raw.trim().toLongOrNull()
                else -> null
            } ?: 0L
            val player = runtimeData["unique.player"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            return DefaultUniqueData(player = player, date = date, uuid = uuid)
        }

    override fun toMap(): Map<String, Any> {
        val values = linkedMapOf<String, Any>(
            "id" to id,
            "amount" to amount
        )
        data?.let { values["data"] = it }
        uniqueData?.toMap()?.let { values["unique"] = it }
        values["version_hash"] = versionHash
        if (metaHistory.isNotEmpty()) {
            values["meta_history"] = metaHistory
        }
        values["item_stack_data"] = itemStackData
        return values
    }

    interface UniqueData : JsonContainer {

        val player: String?

        val date: Long

        val uuid: String

        override fun toMap(): Map<String, Any> {
            val values = linkedMapOf<String, Any>(
                "date" to date,
                "uuid" to uuid
            )
            player?.let { values["player"] = it }
            return values
        }
    }
}

data class DefaultUniqueData(
    override val player: String?,
    override val date: Long,
    override val uuid: String
) : SerializedItem.UniqueData
