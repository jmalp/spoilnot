package com.parcha.spoilnot.feature.recipes.domain

import java.net.URI
import java.net.URLEncoder

/** Recipe page addresses: only http and https, canonicalized without tracking parameters (DESIGN 11.3). */
object SourceUrl {
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val TRACKING = Regex("""^(utm_.*|fbclid|gclid|mc_cid|mc_eid)$""", RegexOption.IGNORE_CASE)
    private val HOST_LIKE = Regex("""^[\p{L}\d-]+(\.[\p{L}\d-]+)+(:\d+)?(/.*)?$""")

    /**
     * Lowercased scheme and host, no fragment, no default port, no `utm_*`. Returns null for anything that is not
     * an absolute http or https URL.
     */
    fun canonical(url: String?): String? {
        val uri = parse(url) ?: return null
        val query = uri.rawQuery
            ?.split('&')
            ?.filter { it.isNotEmpty() && !TRACKING.matches(it.substringBefore('=')) }
            ?.joinToString("&")
            ?.ifEmpty { null }
        val scheme = uri.scheme.lowercase()
        val port = uri.port.takeUnless {
            it == -1 || (scheme == "https" && it == 443) || (scheme == "http" && it == 80)
        }
        val path = uri.rawPath.ifEmpty { "/" }
        return buildString {
            append(scheme).append("://").append(uri.host.lowercase())
            if (port != null) append(':').append(port)
            append(path)
            if (query != null) append('?').append(query)
        }
    }

    /** Two links to the same page: `www.`, http vs https and a trailing slash do not matter (duplicate check). */
    fun sameKey(url: String?): String? {
        val canonical = canonical(url) ?: return null
        return canonical.substringAfter("://").removePrefix("www.").let { k ->
            val path = k.substringBefore('?')
            val query = k.substringAfter('?', "")
            path.trimEnd('/') + if (query.isEmpty()) "" else "?$query"
        }
    }

    /** The first link in shared text, such as "Look at this https://example.com/pie" from the share sheet. */
    fun fromSharedText(text: String?): String? {
        val match = URL_IN_TEXT.find(text ?: return null) ?: return null
        return canonical(match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', '\'', '"'))
    }

    /** Paste link: a link anywhere in the text, or an address typed without https://. */
    fun fromPasted(text: String): String? {
        fromSharedText(text)?.let { return it }
        val t = text.trim()
        return if (' ' !in t && HOST_LIKE.matches(t)) canonical("https://$t") else null
    }

    /**
     * What to load for the browser's address bar: a link as typed (https added when no scheme is given), or a web
     * search for anything else.
     */
    fun fromAddressBar(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("http://", true) || text.startsWith("https://", true)) return canonical(text)
        if (' ' !in text && HOST_LIKE.matches(text)) canonical("https://$text")?.let { return it }
        return "https://www.google.com/search?q=" + URLEncoder.encode(text, "UTF-8")
    }

    /** The cleartext policy blocks plain http (DESIGN 12.2), so pages are requested over https. */
    fun preferHttps(url: String): String = if (url.startsWith("http://", true)) "https://" + url.substring(7) else url

    fun isWebUrl(url: String?): Boolean = parse(url) != null

    /** "www.seriouseats.com" becomes "seriouseats.com", for the source chip. */
    fun siteName(url: String?): String? = parse(url)?.host?.lowercase()?.removePrefix("www.")

    /** [link] resolved against [base], for relative image URLs. */
    fun resolve(base: String?, link: String?): String? {
        val l = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolved = runCatching { if (base == null) URI(l) else URI(base).resolve(URI(l.replace(" ", "%20"))) }
            .getOrNull()
            ?.toString()
        return resolved?.takeIf { isWebUrl(it) }
    }

    private fun parse(url: String?): URI? {
        val text = url?.trim()?.takeIf { it.isNotEmpty() && it.length <= RecipeLimits.URL } ?: return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrEmpty()) return null
        return uri
    }
}
