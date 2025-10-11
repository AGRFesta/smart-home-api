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

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization") ?: ""

        if (!header.startsWith("Bearer ")) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing Authorization header")
            return
        }

        val providedToken = header.removePrefix("Bearer ").trim()
        if (providedToken.isEmpty()) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Empty token")
            return
        }

        if (expectedHash.isBlank()) {
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server misconfigured")
            return
        }

        val providedHashHex = HashUtils.sha256Hex(providedToken)
        if (providedHashHex.lowercase() != expectedHash.lowercase()) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token")
            return
        }

        val auth = UsernamePasswordAuthenticationToken(
            /* principal = */ "api-client",
            /* credentials = */ null,
            /* authorities = */ listOf(SimpleGrantedAuthority("ROLE_API"))
        )
        SecurityContextHolder.getContext().authentication = auth

        // Token valid → continue
        filterChain.doFilter(request, response)
    }

    private fun writeJsonError(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = MessageResponse(message)
        val json = objectMapper.writeValueAsString(body)
        response.writer.use { it.write(json) }
    }
}
