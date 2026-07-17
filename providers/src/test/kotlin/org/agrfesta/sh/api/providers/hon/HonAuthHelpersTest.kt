package org.agrfesta.sh.api.providers.hon

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HonAuthHelpersTest {

    // /// percentEncode() //////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `percentEncode replicates urllib quote`() {
        // Given: vectors computed with python `urllib.parse.quote` (default safe = "/")
        val vectors = mapOf(
            "abcXYZ019" to "abcXYZ019",
            "_.-~/" to "_.-~/",
            "a b" to "a%20b",
            "a+b" to "a%2Bb",
            "k=v&x" to "k%3Dv%26x",
            "café" to "caf%C3%A9",
            """{"a":1}""" to "%7B%22a%22%3A1%7D",
        )

        vectors.forEach { (input, expected) ->
            // When
            val encoded = HonAuthHelpers.percentEncode(input)

            // Then
            withClue("percentEncode(\"$input\") should be \"$expected\"") {
                encoded shouldBe expected
            }
        }
    }

    // /// buildAuthorizeUrl() //////////////////////////////////////////////////////////////////////////////////////

    @Test fun `buildAuthorizeUrl builds the mobile authorize url`() {
        // Given
        val authApi = "https://auth.example.com"
        val clientId = "client-123"
        val nonce = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

        // When
        val url = HonAuthHelpers.buildAuthorizeUrl(authApi, clientId, nonce)

        // Then: spaces in scope MUST be pre-encoded (Ktor sends the URL verbatim), the
        // redirect_uri scheme separator pre-quoted, the response_type '+' kept literal.
        url shouldBe "https://auth.example.com/services/oauth2/authorize/expid_Login?" +
            "response_type=token+id_token" +
            "&client_id=client-123" +
            "&redirect_uri=hon%3A//mobilesdk/detect/oauth/done" +
            "&display=touch" +
            "&scope=api%20openid%20refresh_token%20web" +
            "&nonce=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    }

    // /// extractLoginUrl() ////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `extractLoginUrl extracts the first quoted url from the authorize page`() {
        // Given: spacing as on the real page (`href ='...'`)
        val page = """<script>window.location.href ='/s/login/xyz?startURL=%2Fabc';</script>"""

        // When
        val url = HonAuthHelpers.extractLoginUrl("https://auth.example.com", page)

        // Then
        url shouldBe "/s/login/xyz?startURL=%2Fabc"
    }

    @Test fun `extractLoginUrl rewrites NewhOnLogin paths to the legacy login endpoint`() {
        // Given: the new login page (Jul-2024), the flow only handles the legacy one
        val page = """window.location.href ='/NewhOnLogin?display=touch%2F&ec=302';"""

        // When
        val url = HonAuthHelpers.extractLoginUrl("https://auth.example.com", page)

        // Then
        url shouldBe "https://auth.example.com/s/login/NewhOnLogin?display=touch%2F&ec=302"
    }

    @Test fun `extractLoginUrl returns null when the page carries no login url`() {
        // Given
        val page = "<html><body>nothing to see here</body></html>"

        // When
        val url = HonAuthHelpers.extractLoginUrl("https://auth.example.com", page)

        // Then
        url shouldBe null
    }

    // /// parseTokenFragment() /////////////////////////////////////////////////////////////////////////////////////

    @Test fun `parseTokenFragment extracts the three tokens from the redirect fragment`() {
        // Given: percent-encoded refresh token, extra params after the tokens
        val page = "hon://mobilesdk/detect/oauth/done#access_token=aaa.bbb&" +
            "refresh_token=5Aep%2Babc&id_token=eyJx&token_type=Bearer"

        // When
        val tokens = HonAuthHelpers.parseTokenFragment(page)

        // Then: ONLY the refresh token is url-decoded
        tokens shouldBe HonOAuthTokens(
            accessToken = "aaa.bbb",
            refreshToken = "5Aep+abc",
            idToken = "eyJx",
            complete = true,
        )
    }

    @Test fun `parseTokenFragment preserves literal plus signs in the refresh token`() {
        // Given: a raw '+' in the fragment (urllib.unquote keeps it, URLDecoder would not)
        val page = "oauth/done#access_token=a&refresh_token=5Aep+861.abc&id_token=i&x=y"

        // When
        val tokens = HonAuthHelpers.parseTokenFragment(page)

        // Then
        tokens.refreshToken shouldBe "5Aep+861.abc"
    }

    @Test fun `parseTokenFragment captures the last fragment field at end of text`() {
        // Given: the real done-URL ends with id_token as the LAST field, no trailing
        // delimiter (live-verified 2026-07-17: the "incomplete OAuth tokens" 502)
        val page = "hon://mobilesdk/detect/oauth/done#access_token=aaa.bbb&" +
            "refresh_token=5Aep%2Babc&id_token=eyJx"

        // When
        val tokens = HonAuthHelpers.parseTokenFragment(page)

        // Then
        tokens shouldBe HonOAuthTokens(
            accessToken = "aaa.bbb",
            refreshToken = "5Aep+abc",
            idToken = "eyJx",
            complete = true,
        )
    }

    @Test fun `parseTokenFragment stops at a double quote closing a JS redirect url`() {
        // Given: the fragment inside a JS string, more page text (with '&') after it —
        // the capture must stop at the closing quote, not swallow up to the next '&'
        val page = """window.location.replace("hon://mobilesdk/detect/oauth/done#""" +
            """access_token=aaa&refresh_token=rrr&id_token=eyJx");</script><a href="/x?a=1&b=2">"""

        // When
        val tokens = HonAuthHelpers.parseTokenFragment(page)

        // Then
        tokens shouldBe HonOAuthTokens(
            accessToken = "aaa",
            refreshToken = "rrr",
            idToken = "eyJx",
            complete = true,
        )
    }

    @Test fun `parseTokenFragment stops at a single quote closing a JS redirect url`() {
        // Given: same trap with the single-quote style (`href ='...'`)
        val page = "window.location.href ='hon://oauth/done#access_token=aaa&" +
            "refresh_token=rrr&id_token=eyJx';var leftover=1&more=2"

        // When
        val tokens = HonAuthHelpers.parseTokenFragment(page)

        // Then
        tokens shouldBe HonOAuthTokens(
            accessToken = "aaa",
            refreshToken = "rrr",
            idToken = "eyJx",
            complete = true,
        )
    }

    @Test fun `parseTokenFragment flags incomplete extraction when a token is missing`() {
        // Given: no id_token in the fragment
        val page = "oauth/done#access_token=aaa&refresh_token=rrr&x=y"

        // When
        val tokens = HonAuthHelpers.parseTokenFragment(page)

        // Then
        withClue("extraction should be incomplete without id_token") {
            tokens.complete shouldBe false
        }
    }

    // /// absolutize() /////////////////////////////////////////////////////////////////////////////////////////////

    @Test fun `absolutize resolves a relative href against the auth host`() {
        // Given
        val href = "/finaltok?x=1"

        // When
        val resolved = HonAuthHelpers.absolutize("https://auth.example.com", href)

        // Then
        resolved shouldBe "https://auth.example.com/finaltok?x=1"
    }

    @Test fun `absolutize pins an off-host http url back onto the auth host`() {
        // Given: the login flow never legitimately leaves the auth host
        val href = "https://evil.example.org/path?q=1"

        // When
        val resolved = HonAuthHelpers.absolutize("https://auth.example.com", href)

        // Then: foreign authority demoted to a path segment
        resolved shouldBe "https://auth.example.com/evil.example.org/path?q=1"
    }

    @Test fun `absolutize leaves non-http schemes untouched`() {
        // Given: the app-scheme redirect that ends the flow
        val href = "hon://mobilesdk/detect/oauth/done#access_token=a&"

        // When
        val resolved = HonAuthHelpers.absolutize("https://auth.example.com", href)

        // Then
        resolved shouldBe href
    }

    // /// isProgressiveOtp() ///////////////////////////////////////////////////////////////////////////////////////

    @Test fun `isProgressiveOtp detects the email OTP page markers`() {
        // Given: the three markers of the 2FA OTP step (JS-remoting controller,
        // verify action, email-code input)
        val page = """
            <script>ProgressiveLoginController.verifyEmailOTP(code);</script>
            <input type="text" name="emailcode" />
        """.trimIndent()

        // When
        val isOtp = HonAuthHelpers.isProgressiveOtp(page)

        // Then
        isOtp shouldBe true
    }

    @Test fun `isProgressiveOtp is false on a page without the OTP markers`() {
        // Given: a ProgressiveLogin page that is NOT the OTP step
        val page = """<html><a href="/setup/secur/RemoteAccessAuthorizationPage.apexp">continue</a></html>"""

        // When
        val isOtp = HonAuthHelpers.isProgressiveOtp(page)

        // Then
        isOtp shouldBe false
    }
}
