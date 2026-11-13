package com.nexonic.downloadmanager.core

import java.util.Locale

/**
 * Describes the inspected yt-dlp selector honestly. The resolution selector
 * is capped (height <= N), NOT an exact stream promise.
 */
object HandoffFormatDisplay {
    fun label(choice: VideoChoice): String {
        val extension = choice.ext.uppercase(Locale.ROOT)
        if (choice.audioOnly) return extension
        val limit = HandoffQualityPolicy.heightFromLabel(choice.label)
        return if (limit != null) "Up to ${limit}p · $extension" else "Auto · $extension"
    }
}
