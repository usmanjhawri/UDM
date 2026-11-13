package com.nexonic.downloadmanager.core

/**
 * Metadata supplied by external Android handoff (including YouTube Morphe)
 * or by another app's share action. No embedded browser is required.
 */
data class Offer(
    val url: String,
    val fileName: String? = null,
    val mime: String? = null,
    val size: Long = -1,
    val referer: String? = null,
    val userAgent: String? = null,
    val cookies: String? = null,
    val quality: String? = null,
)
