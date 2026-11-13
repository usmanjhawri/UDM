package com.nexonic.downloadmanager.core

import android.content.Context

/** Remember only last media type and resolution; never secrets or yt-dlp selectors. */
object HandoffPreference {
    private const val STORE = "nexonic_personal_handoff_preferences"
    private const val KIND = "youtube_kind"
    private const val HEIGHT = "youtube_resolution"

    fun pick(context: Context, choices: List<VideoChoice>): Int {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val kind = prefs.getString(KIND, null)
        val preferredAudio = when (kind) { "audio" -> true; "video" -> false; else -> null }
        val preferredHeight = prefs.getInt(HEIGHT, -1).takeIf { it > 0 }
        return HandoffQualityPolicy.choose(choices.map {
            HandoffFormatHint(it.audioOnly, HandoffQualityPolicy.heightFromLabel(it.label))
        }, preferredAudio, preferredHeight)
    }

    fun remember(context: Context, choice: VideoChoice) {
        val editor = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KIND, if (choice.audioOnly) "audio" else "video")
        val height = HandoffQualityPolicy.heightFromLabel(choice.label)
        if (height == null) editor.remove(HEIGHT) else editor.putInt(HEIGHT, height)
        editor.apply()
    }
}
