package com.nexonic.downloadmanager.core

/** Published SHA-256 for the exact official yt-dlp 2026.10.26 executable.
 * User-selected files are untrusted until both size and digest match.
 */
object OfficialYtDlpRelease {
    const val TAG = "2026.10.26"
    const val MAX_BYTES = 12L * 1024 * 1024
    const val SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"

    fun valid(size: Long, digest: ByteArray): Boolean {
        if (size != 3073072L || digest.size != 32) return false
        val observed = digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return observed.equals(SHA256, ignoreCase = true)
    }
}
