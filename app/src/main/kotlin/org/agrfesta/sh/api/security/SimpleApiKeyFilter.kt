package org.agrfesta.sh.api.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.agrfesta.sh.api.controllers.MessageResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class SimpleApiKeyFilter(
    @Value("\${security.api-token-hash}") private val expectedHash: String,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    @Suppress("ReturnCount")
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Public health probes are never blocked by a missing/invalid token; a valid token still
        // elevates the caller (e.g. to see health component details).
        val publicPath = allowsAnonymous(request)
        val header = request.getHeader("Authorization") ?: ""

        if (!header.startsWith("Bearer ")) {
            rejectOrContinue(publicPath, "Missing Authorization header", request, response, filterChain)
            return
        }

        val providedToken = header.removePrefix("Bearer ").trim()
        if (providedToken.isEmpty()) {
            rejectOrContinue(publicPath, "Empty token", request, response, filterChain)
            return
        }

        if (expectedHash.isBlank()) {
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server misconfigured")
            return
        }

        val providedHashHex = HashUtils.sha256Hex(providedToken)
        if (providedHashHex.lowercase() != expectedHash.lowercase()) {
            rejectOrContinue(publicPath, "Invalid token", request, response, filterChain)
            return
        }

        val auth = UsernamePasswordAuthenticationToken(
            "api-client",
            null,
            listOf(SimpleGrantedAuthority("ROLE_API"))
        )
        SecurityContextHolder.getContext().authentication = auth

        // Token valid → continue
        filterChain.doFilter(request, response)
    }

    /** On a public path, let the request through anonymously; otherwise reject with 401. */
    private fun rejectOrContinue(
        publicPath: Boolean,
        message: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (publicPath) {
            filterChain.doFilter(request, response)
        } else {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, message)
        }
    }

    private fun allowsAnonymous(request: HttpServletRequest): Boolean =
        request.servletPath in PUBLIC_HEALTH_ENDPOINTS

    private fun writeJsonError(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = MessageResponse(message)
        val json = objectMapper.writeValueAsString(body)
        response.writer.use { it.write(json) }
    }
}
