package com.nexonic.downloadmanager.core

/** Colour schemes (the same built-in ones as the desktop apps): a light or dark base, an accent and a background. */
data class ColorSchemeChoice(val name: String, val dark: Boolean, val accent: Long, val background: Long, val surface: Long) {
    companion object {
        val builtIn = listOf(
            ColorSchemeChoice("Ocean", true, 0xFF3AA0F2, 0xFF0E1A24, 0xFF13222F),
            ColorSchemeChoice("Forest", false, 0xFF2E7D32, 0xFFF4F8F3, 0xFFFFFFFF),
            ColorSchemeChoice("Midnight", true, 0xFF8B7CF6, 0xFF0B0B10, 0xFF14141C),
            ColorSchemeChoice("High Contrast", true, 0xFFFFD400, 0xFF000000, 0xFF000000),
        )
        fun named(name: String?) = builtIn.firstOrNull { it.name == name } ?: custom(name)

        /** The user's own scheme, saved as "custom:dark:#RRGGBB:#RRGGBB" (light or dark, accent, background). */
        fun custom(code: String?): ColorSchemeChoice? {
            val p = code?.split(':') ?: return null
            if (p.size != 4 || p[0] != "custom" || p[1] !in setOf("dark", "light")) return null
            val accent = rgb(p[2]) ?: return null
            val background = rgb(p[3]) ?: return null
            val dark = p[1] == "dark"
            // Panels: a little lighter than a dark background, white on a light one.
            val surface = if (dark) mix(background, 0xFFFFFFFF, 0.06) else 0xFFFFFFFF
            return ColorSchemeChoice(code, dark, accent, background, surface)
        }

        fun customCode(dark: Boolean, accent: String, background: String) =
            "custom:${if (dark) "dark" else "light"}:${accent.uppercase()}:${background.uppercase()}"

        /** "#RRGGBB" → 0xFFRRGGBB, or null. */
        fun rgb(hex: String): Long? {
            val h = hex.trim().removePrefix("#")
            if (h.length != 6) return null
            return h.toLongOrNull(16)?.let { 0xFF000000 or it }
        }

        private fun mix(a: Long, b: Long, t: Double): Long {
            fun ch(c: Long, s: Int) = (c shr s) and 0xFF
            fun m(s: Int) = Math.round(ch(a, s) * (1 - t) + ch(b, s) * t)
            return 0xFF000000 or (m(16) shl 16) or (m(8) shl 8) or m(0)
        }

        /** WCAG contrast ratio (1 to 21); 3 or more keeps the accent readable on the background. */
        fun contrast(a: Long, b: Long): Double {
            fun lum(c: Long): Double {
                fun ch(s: Int): Double { val v = ((c shr s) and 0xFF) / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
                return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
            }
            val l1 = lum(a); val l2 = lum(b)
            return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
        }
    }
}
