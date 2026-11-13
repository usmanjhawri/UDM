package com.nexonic.downloadmanager.core

import java.io.IOException
import java.util.Locale
import org.json.JSONObject

enum class DebridService(val title: String) { NONE("Off"), REAL_DEBRID("Real-Debrid"), ALL_DEBRID("AllDebrid"), PREMIUMIZE("Premiumize") }

/** An optional debrid account (your own API key; off by default), used only for the sites listed. */
object Debrid {
    data class Request(val url: String, val form: Map<String, String>, val bearer: String?)

    fun applies(url: String, service: DebridService, key: String, hosts: String): Boolean {
        if (service == DebridService.NONE || key.isBlank()) return false
        val host = runCatching { java.net.URI(url) }.getOrNull()?.takeIf { it.scheme.equals("http", true) || it.scheme.equals("https", true) }
            ?.host?.lowercase(Locale.ROOT) ?: return false
        return hosts.split(Regex("[,;\\s]+")).map { it.trim().lowercase(Locale.ROOT).trimStart('.') }
            .any { it.isNotEmpty() && (host == it || host.endsWith(".$it")) }
    }

    fun request(service: DebridService, key: String, link: String): Request = when (service) {
        DebridService.REAL_DEBRID -> Request("https://api.real-debrid.com/rest/1.0/unrestrict/link", mapOf("link" to link), key.trim())
        DebridService.ALL_DEBRID -> Request("https://api.alldebrid.com/v4/link/unlock?agent=nexonic", mapOf("link" to link), key.trim())
        DebridService.PREMIUMIZE -> Request("https://www.premiumize.me/api/transfer/directdl", mapOf("apikey" to key.trim(), "src" to link), null)
        DebridService.NONE -> throw IOException("No debrid service is chosen.")
    }

    fun parseAnswer(service: DebridService, json: String): String {
        val o = runCatching { JSONObject(json) }.getOrDefault(JSONObject())
        val link = when (service) {
            DebridService.REAL_DEBRID -> o.optString("download")
            DebridService.ALL_DEBRID -> o.optJSONObject("data")?.optString("link")
            DebridService.PREMIUMIZE -> o.optJSONArray("content")?.optJSONObject(0)?.optString("link")?.ifEmpty { null } ?: o.optString("location")
            DebridService.NONE -> null
        }
        if (!link.isNullOrEmpty() && Links.isWebLink(link)) return link
        val message = o.optJSONObject("error")?.optString("message") ?: o.optString("error").ifEmpty { o.optString("message") }
        throw IOException("${service.title} could not unlock the link" + if (message.isNullOrEmpty()) "." else ": $message")
    }

    fun formBody(form: Map<String, String>): String =
        form.entries.sortedBy { it.key }.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
}

