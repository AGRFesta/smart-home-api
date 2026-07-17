package org.agrfesta.sh.api.providers.hon

import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.http.encodeCookieValue

/**
 * [AcceptAllCookiesStorage] that never breaks on replay.
 *
 * Ktor re-encodes each cookie it sends with the encoding recorded on the cookie, and cookies
 * parsed from `Set-Cookie` default to [CookieEncoding.RAW]: a Salesforce consent/bot-detection
 * cookie whose value carries spaces, commas or quotes then throws `IllegalArgumentException`
 * ("cannot be encoded in RAW format") at request time, breaking the whole login flow.
 *
 * Each cookie is stored with the most conservative encoding able to represent its value:
 * [CookieEncoding.RAW] (byte-identical) when possible, [CookieEncoding.DQUOTES] (RFC 6265
 * quoted form) otherwise, [CookieEncoding.URI_ENCODING] as a last resort for values containing
 * double quotes. Cookies are never dropped: losing one (e.g. bot-detection) could fail the
 * login in subtler ways.
 */
internal class LenientCookiesStorage : CookiesStorage {
    private val delegate = AcceptAllCookiesStorage()

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        delegate.addCookie(requestUrl, cookie.copy(encoding = cookie.value.safestEncoding()))
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = delegate.get(requestUrl)

    override fun close() = delegate.close()

    private fun String.safestEncoding(): CookieEncoding =
        listOf(CookieEncoding.RAW, CookieEncoding.DQUOTES)
            .firstOrNull { canEncodeAs(it) }
            ?: CookieEncoding.URI_ENCODING

    private fun String.canEncodeAs(encoding: CookieEncoding): Boolean =
        runCatching { encodeCookieValue(this, encoding) }.isSuccess
}
