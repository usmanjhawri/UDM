package com.nexonic.downloadmanager.core

/**
 * Privacy-safe, actionable status messages for failures from the yt-dlp Android updater.
 * Do not show signed media URLs, credentials, cookies, or verbose exception traces in UI.
 */
object EngineUpdateDiagnostics {
    fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(6).toList()
        val combined = chain.joinToString(" | ") { it.javaClass.simpleName + ": " + (it.message ?: "") }
        val lower = combined.lowercase()
        return when {
            "unknownhost" in lower || "unable to resolve host" in lower || "name or service not known" in lower ->
                "Unable to resolve the update server (DNS). Check Wi-Fi, DNS, VPN, or network restrictions."
            "sockettimeout" in lower || "timed out" in lower ->
                "The update server timed out. Retry on a reliable connection."
            "ssl" in lower || "certificate" in lower ->
                "Secure connection to the update server failed. Check the device clock and network."
            "http 403" in lower || "http error 403" in lower || "response code 403" in lower ->
                "The update server denied access (HTTP 403). Try another connection."
            "http 404" in lower || "response code 404" in lower ->
                "The update package was not found (HTTP 404). Try a different update channel."
            "no space left" in lower || "enospc" in lower ->
                "Not enough phone storage to install the updated engine."
            "permission denied" in lower || "access denied" in lower ->
                "Engine update couldn't write its files. Check storage availability."
            else -> {
                val root = chain.last()
                val safe = root.message.orEmpty()
                    .replace(Regex("""https?://\S+""", RegexOption.IGNORE_CASE), "[link]")
                    .replace(Regex("""(?i)(token|key|signature|cookie)=[^\s&]+"""), "$1=[redacted]")
                    .replace('\n', ' ')
                    .take(145)
                root.javaClass.simpleName + (if (safe.isBlank()) " (no further details)" else ": $safe")
            }
        }
    }
}
