package org.agrfesta.sh.api.providers.hon

import java.net.URI
import java.net.URLDecoder

/**
 * Pure helpers of the hOn Salesforce login flow.
 *
 * Ported and adapted from the addhOn project (https://github.com/tis24dev/addhOn,
 * MIT License, Copyright (c) tis24dev) — `client/transport/{auth,oauth}.py` and
 * `examples/ktor-client`. The encodings and page-scraping rules replicated here are
 * the reverse-engineered contract of the hOn cloud: change them only against a live
 * verification.
 */
internal object HonAuthHelpers {

    // Characters urllib.parse.quote leaves untouched (default safe = "/"): the aura
    // body and the redirect_uri must be encoded EXACTLY like this.
    private val QUOTE_SAFE: Set<Char> = buildSet {
        addAll('a'..'z')
        addAll('A'..'Z')
        addAll('0'..'9')
        addAll(listOf('_', '.', '-', '~', '/'))
    }

    fun percentEncode(s: String): String = buildString {
        for (byte in s.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and BYTE_MASK
            val char = code.toChar()
            if (code < ASCII_LIMIT && char in QUOTE_SAFE) {
                append(char)
            } else {
                append('%')
                append("%02X".format(code))
            }
        }
    }

    /**
     * OAuth authorize URL (mobile login). Query built by hand, as the app does: the '+'
     * in response_type stays literal, the redirect_uri is pre-quoted, and the scope
     * spaces are pre-encoded as %20 — Ktor sends the URL verbatim (no re-encoding at
     * send time, unlike aiohttp/yarl) and literal spaces would produce a malformed
     * HTTP request line (verified live, 2026-07-16).
     */
    fun buildAuthorizeUrl(authApi: String, clientId: String, nonce: String): String {
        val redirectUri = percentEncode("hon://mobilesdk/detect/oauth/done")
        return "$authApi/services/oauth2/authorize/expid_Login?" +
            "response_type=token+id_token" +
            "&client_id=$clientId" +
            "&redirect_uri=$redirectUri" +
            "&display=touch" +
            "&scope=api%20openid%20refresh_token%20web" +
            "&nonce=$nonce"
    }

    // First `url='...'` or `href='...'` link on the authorize page.
    private val LOGIN_URL_RE = Regex("(?:url|href) ?= ?'(.+?)'")

    /**
     * Login url from the authorize page, or null if absent. The relative `/NewhOnLogin...`
     * (new login page, Jul-2024) is rewritten onto the legacy `/s/login` endpoint, the
     * only one the flow knows how to drive.
     */
    fun extractLoginUrl(authApi: String, text: String): String? {
        val url = LOGIN_URL_RE.find(text)?.groupValues?.get(1) ?: return null
        return if (url.startsWith("/NewhOnLogin")) "$authApi/s/login$url" else url
    }

    /**
     * Extracts the three tokens from the OAuth redirect fragment
     * (`...oauth/done#access_token=...&refresh_token=...&id_token=...`).
     * Rules inherited from addhOn: the regex requires the trailing `&` (a token at the
     * end of the text without `&` is not captured) and ONLY the refresh_token is
     * url-decoded — protecting literal `+` (URLDecoder would treat it as a space,
     * urllib.unquote, the reference, does not).
     */
    fun parseTokenFragment(text: String): HonOAuthTokens {
        fun match(name: String) = Regex("$name=(.*?)&").find(text)?.groupValues?.get(1)
        val access = match("access_token")
        val refresh = match("refresh_token")
        val id = match("id_token")
        return HonOAuthTokens(
            accessToken = access ?: "",
            refreshToken = refresh?.let {
                URLDecoder.decode(it.replace("+", "%2B"), Charsets.UTF_8)
            } ?: "",
            idToken = id ?: "",
            complete = access != null && refresh != null && id != null,
        )
    }

    /**
     * Resolves an href (relative or absolute) against the auth host and PINS it onto
     * that host: the login flow never legitimately leaves the auth host, so an off-host
     * http(s) result is brought back with the foreign authority demoted to a path
     * segment. Non-http schemes (e.g. `hon://`) are returned untouched.
     */
    fun absolutize(authApi: String, href: String): String {
        val resolved = runCatching { URI(authApi).resolve(href).toString() }
            .getOrDefault(authApi + href)
        val uri = runCatching { URI(resolved) }.getOrNull() ?: return resolved
        val authHost = URI(authApi).host
        if (uri.scheme in FETCHABLE_SCHEMES && uri.host != authHost) {
            val demoted = "/" + ((uri.host ?: "") + (uri.rawPath ?: "")).trimStart('/')
            val query = uri.rawQuery?.let { "?$it" } ?: ""
            return "https://$authHost$demoted$query"
        }
        return resolved
    }

    private val FETCHABLE_SCHEMES = listOf("http", "https", "ws", "wss")

    // Tolerant of quote style / spaces so a template tweak does not silently turn the
    // OTP page into an undetected "no href" failure.
    private val EMAILCODE_RE = Regex("""name\s*=\s*["']emailcode["']""", RegexOption.IGNORE_CASE)

    /** True if the ProgressiveLogin page is the email-OTP step of the 2FA. */
    fun isProgressiveOtp(page: String): Boolean {
        val low = page.lowercase()
        return "progressivelogincontroller" in low &&
            "verifyemailotp" in low &&
            EMAILCODE_RE.containsMatchIn(low)
    }

    private const val BYTE_MASK = 0xFF
    private const val ASCII_LIMIT = 128
}

/** The three tokens extracted from the OAuth redirect fragment. [complete] = all present. */
internal data class HonOAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val complete: Boolean,
)
