package com.nexonic.downloadmanager.core

/** Abstract quality hint; never persist URLs, selectors, cookies or format IDs. */
data class HandoffFormatHint(val audioOnly: Boolean, val height: Int?)

object HandoffQualityPolicy {
    private val heightRegex = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)

    fun heightFromLabel(label: String): Int? = heightRegex.find(label)
        ?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 144..8640 }

    /** Exact height, then closest lower, then lowest above, in the preferred media type. */
    fun choose(options: List<HandoffFormatHint>, preferredAudio: Boolean?, preferredHeight: Int?): Int {
        if (options.isEmpty()) return -1
        if (preferredAudio == null) return 0
        val matches = options.withIndex().filter { it.value.audioOnly == preferredAudio }
        if (matches.isEmpty()) return 0
        if (preferredAudio || preferredHeight == null) return matches.first().index
        matches.firstOrNull { it.value.height == preferredHeight }?.let { return it.index }
        val lower = matches.filter { (it.value.height ?: 0) <= preferredHeight && it.value.height != null }
            .maxByOrNull { it.value.height ?: -1 }
        val above = matches.filter { it.value.height != null }.minByOrNull { it.value.height ?: Int.MAX_VALUE }
        return (lower ?: above ?: matches.first()).index
    }
}
