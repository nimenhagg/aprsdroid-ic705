package org.aprsdroid.app.map

/** Expands the optional {s} placeholder into concrete MapLibre tile endpoints. */
object TileUrlTemplate {
    fun expand(pattern: String, subdomains: String = ""): Array<String> {
        val normalized = pattern.trim()
        if (normalized.isEmpty()) return emptyArray()
        if (!normalized.contains("{s}")) return arrayOf(normalized)

        val uniqueSubdomains = subdomains.toList().distinct()
        if (uniqueSubdomains.isEmpty()) return arrayOf(normalized.replace("{s}", ""))
        return uniqueSubdomains.map { normalized.replace("{s}", it.toString()) }.toTypedArray()
    }
}
