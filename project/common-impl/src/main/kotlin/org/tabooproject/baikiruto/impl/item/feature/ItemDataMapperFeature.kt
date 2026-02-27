package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.command.CommandSender
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.impl.item.DefaultItemStream
import org.tabooproject.baikiruto.impl.log.BaikirutoLog

object ItemDataMapperFeature {

    const val DATA_MAPPER_KEY: String = "__baikiruto_data_mapper"

    fun apply(stream: DefaultItemStream, context: Map<String, Any?>) {
        val rawMapper = stream.getRuntimeData(DATA_MAPPER_KEY) as? Map<*, *> ?: return
        if (rawMapper.isEmpty()) {
            return
        }
        val sender = context["sender"] as? CommandSender
        rawMapper.forEach { (rawKey, rawSource) ->
            val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val source = parseSource(rawSource)
            if (source.isBlank()) {
                return@forEach
            }
            val variables = LinkedHashMap<String, Any?>()
            variables.putAll(context)
            variables["stream"] = stream
            variables["itemId"] = stream.itemId
            variables["data"] = stream.runtimeData
            variables["it"] = stream.getRuntimeData(key)
            val mapped = runCatching {
                Baikiruto.api().getScriptHandler().invoke(
                    source = source,
                    id = "item-data-mapper:${stream.itemId}:$key",
                    sender = sender,
                    variables = variables
                )
            }.onFailure {
                BaikirutoLog.scriptRuntimeFailed("${stream.itemId}:data-mapper:$key", it)
            }.getOrNull() ?: return@forEach
            stream.setRuntimeData(key, mapped)
            stream.markSignal(ItemSignal.DATA_MAPPED)
        }
    }

    private fun parseSource(rawSource: Any?): String {
        return when (rawSource) {
            is String -> rawSource
            is Iterable<*> -> rawSource.mapNotNull { it?.toString() }.joinToString("\n")
            else -> rawSource?.toString().orEmpty()
        }
    }
}
