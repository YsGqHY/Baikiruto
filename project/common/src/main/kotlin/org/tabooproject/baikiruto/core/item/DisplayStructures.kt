package org.tabooproject.baikiruto.core.item

private val TOKEN_PATTERN = Regex("<([^<>]+)>")

class DefaultStructureSingle(
    private val source: String
) : StructureSingle {

    override fun build(vars: Map<String, String>, trim: Boolean): String {
        val rendered = TOKEN_PATTERN.replace(source) { match ->
            val key = match.groupValues[1].trim()
            if (key.isEmpty()) {
                return@replace ""
            }
            vars[key]
                ?: vars[key.lowercase()]
                ?: vars[key.uppercase()]
                ?: ""
        }
        return if (trim) rendered.trim() else rendered
    }
}

class DefaultStructureList(
    source: List<String>
) : StructureList {

    private val templates = source.toList()

    override fun build(vars: Map<String, List<String>>, trim: Boolean): List<String> {
        val mutableVars = vars.mapValues { (_, value) -> value.toMutableList() }.toMutableMap()
        val output = arrayListOf<String>()
        templates.forEach { template ->
            output += buildTemplate(template, mutableVars, trim)
        }
        return output
    }

    private fun buildTemplate(
        template: String,
        vars: MutableMap<String, MutableList<String>>,
        trim: Boolean
    ): List<String> {
        val variables = TOKEN_PATTERN.findAll(template)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val ellipsisVariables = variables
            .filter { it.endsWith("...") && it.length > 3 }
            .distinct()
        if (ellipsisVariables.isEmpty()) {
            val rendered = renderOnce(template, vars)
            return listOf(if (trim) rendered.trim() else rendered)
        }
        val output = arrayListOf<String>()
        while (true) {
            var repeat = false
            var skip = false
            var rendered = template
            ellipsisVariables.forEach { variable ->
                val key = variable.dropLast(3)
                val values = lookupMutableList(vars, key)
                if (values.isNullOrEmpty()) {
                    skip = true
                    rendered = replaceFirstLiteral(rendered, "<$variable>", "")
                } else {
                    rendered = replaceFirstLiteral(rendered, "<$variable>", values.removeAt(0))
                    if (values.isNotEmpty()) {
                        repeat = true
                    }
                }
            }
            rendered = renderOnce(rendered, vars)
            if (!skip) {
                output += if (trim) rendered.trim() else rendered
            }
            if (!repeat) {
                break
            }
        }
        return output
    }

    private fun renderOnce(
        source: String,
        vars: Map<String, List<String>>
    ): String {
        return TOKEN_PATTERN.replace(source) { match ->
            val raw = match.groupValues[1].trim()
            if (raw.isEmpty()) {
                return@replace ""
            }
            val key = if (raw.endsWith("...") && raw.length > 3) raw.dropLast(3) else raw
            lookupList(vars, key)?.firstOrNull() ?: ""
        }
    }

    private fun lookupList(map: Map<String, List<String>>, key: String): List<String>? {
        return map[key]
            ?: map[key.lowercase()]
            ?: map[key.uppercase()]
    }

    private fun lookupMutableList(
        map: Map<String, MutableList<String>>,
        key: String
    ): MutableList<String>? {
        return map[key]
            ?: map[key.lowercase()]
            ?: map[key.uppercase()]
    }

    private fun replaceFirstLiteral(source: String, target: String, value: String): String {
        val index = source.indexOf(target)
        if (index < 0) {
            return source
        }
        return buildString(source.length - target.length + value.length) {
            append(source, 0, index)
            append(value)
            append(source, index + target.length, source.length)
        }
    }
}
